package dev.ncn.worlddegrade.compat.opac;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.config.ClaimRemovalTiming;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.schedule.ScheduleResult;
import dev.ncn.worlddegrade.schedule.ScheduleService;
import dev.ncn.worlddegrade.schedule.ScheduleSource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.event.api.v2.OPACServerAddonRegisterEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Open Parties and Claims integration (#6). When an OPAC claim expires, its chunks are collected and
 * fed into the schedule system (#5) so the abandoned base degrades progressively; once the configured
 * pass has run, the expired claim is dropped so the ruin is free to loot.
 *
 * <p>This class owns every OPAC type reference in the mod. It never touches the degradation pipeline
 * directly — scheduling and the "is the job slot free" check both go through {@link ScheduleService},
 * so OPAC-triggered runs inherit the schedule system's no-undo guarantee (an acceptance criterion) and
 * this package never imports {@code DegradeJob}/{@code UndoManager}.
 */
public final class OpacCompat implements ModCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ExpiredChunkBatcher batcher = new ExpiredChunkBatcher(ScheduleService.MAX_CHUNKS);
    // Chunks awaiting an unclaim, per dimension. Drained only while the job slot is free so an unclaim
    // never frees an area mid-pass. In-memory only: if lost at shutdown it is simply re-derived.
    private final Map<ResourceLocation, LongOpenHashSet> unclaimQueue = new HashMap<>();

    @Override
    public String modId() {
        return "openpartiesandclaims";
    }

    @Override
    public List<DegradeEffect> createEffects() {
        // OPAC adds no block-level effect; it only feeds chunks into the schedule system.
        return List.of();
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(this::onAddonRegister);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        ScheduleService.onPassFired(this::onPassFired);
        ScheduleService.setOpacSimulator(this::simulateExpiration);
    }

    /**
     * Drives the same path OPAC's real {@code onChunkChange} callback does so
     * {@code /degrade opac simulate} exercises batching, OPAC-sourced scheduling and the post-pass
     * unclaim without waiting on OPAC's hours-long inactivity timer.
     *
     * <p>With {@code expireClaims} false the chunks are pushed straight into the debounced batcher and no
     * claim data is touched — degradation runs, but the unclaim step finds no expired claim to drop
     * (safe to run over live claims). With {@code expireClaims} true the chunks are first rewritten to
     * OPAC's {@link SpecialClaimOwners#EXPIRED} owner exactly as a real expiration does; that
     * {@code claim} call fires OPAC's {@code onChunkChange}, so {@link ClaimExpirationListener} batches
     * them itself and the post-pass unclaim genuinely removes them. This overwrites whatever claim was
     * there, so it is opt-in.
     */
    private int simulateExpiration(ServerLevel level, LongOpenHashSet chunks, boolean expireClaims) {
        ResourceLocation dimension = level.dimension().location();
        if (expireClaims) {
            IServerClaimsManagerAPI claims = OpenPACServerAPI.get(level.getServer()).getServerClaimsManager();
            for (long chunk : chunks) {
                claims.claim(dimension, SpecialClaimOwners.EXPIRED, 0,
                        ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }
        } else {
            int now = level.getServer().getTickCount();
            for (long chunk : chunks) {
                batcher.add(dimension, chunk, now);
            }
        }
        return chunks.size();
    }

    private void onAddonRegister(OPACServerAddonRegisterEvent event) {
        event.getContext().getClaimsManagerTrackerAPI()
                .register(new ClaimExpirationListener(event.getContext().getServer(), batcher));
    }

    private void onServerStarted(ServerStartedEvent event) {
        if (WorldDegradeConfig.opacEnabled() && !WorldDegradeConfig.scheduleEnabled()) {
            LOGGER.warn("World Degrade: [opac].enabled is true but [schedule].enabled is false; OPAC "
                    + "claim expirations will be ignored until the schedule feature is turned on.");
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!batcher.isEmpty()) {
            for (ExpiredChunkBatcher.Batch batch : batcher.drainReady(server.getTickCount())) {
                scheduleBatch(server, batch);
            }
        }
        if (!unclaimQueue.isEmpty() && ScheduleService.isIdle()) {
            drainUnclaim(server);
        }
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        // Persist any in-flight batch as a real schedule rather than losing it on shutdown.
        for (ExpiredChunkBatcher.Batch batch : batcher.drainAll()) {
            scheduleBatch(server, batch);
        }
        if (!unclaimQueue.isEmpty() && ScheduleService.isIdle()) {
            drainUnclaim(server);
        }
        unclaimQueue.clear();
    }

    private void scheduleBatch(MinecraftServer server, ExpiredChunkBatcher.Batch batch) {
        if (!WorldDegradeConfig.opacEnabled()) {
            return;
        }
        ResourceLocation dimension = batch.dimension();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            LOGGER.debug("World Degrade: dropping OPAC expiration batch for unknown dimension {}", dimension);
            return;
        }
        LongOpenHashSet chunks = batch.chunks();
        // For SCHEDULE timing, capture the chunks this schedule will actually own (those not already
        // covered by another schedule) so we unclaim exactly those, and only if the schedule is created.
        LongOpenHashSet scheduleTimingUnclaim = null;
        if (WorldDegradeConfig.opacRemoveClaimAfter() == ClaimRemovalTiming.SCHEDULE) {
            scheduleTimingUnclaim = new LongOpenHashSet(chunks.size());
            for (long chunk : chunks) {
                if (!ScheduleService.isScheduled(level, chunk)) {
                    scheduleTimingUnclaim.add(chunk);
                }
            }
        }
        ScheduleResult result = ScheduleService.schedule(level, chunks, ScheduleSource.OPAC);
        if (result.created()) {
            LOGGER.info("World Degrade: OPAC claim expiration created schedule #{} over {} chunk(s) in {}",
                    result.id(), result.chunkCount(), dimension);
            if (scheduleTimingUnclaim != null && !scheduleTimingUnclaim.isEmpty()) {
                enqueueUnclaim(dimension, scheduleTimingUnclaim);
            }
        } else {
            // DISABLED / DIMENSION_DISABLED / ALREADY_SCHEDULED are all ordinary outcomes here.
            LOGGER.debug("World Degrade: OPAC expiration in {} created no schedule ({})",
                    dimension, result.status());
        }
    }

    private void onPassFired(ServerLevel level, ScheduleSource source, LongOpenHashSet chunks,
                             int passIndex, boolean firstPass, boolean finalPass) {
        if (source != ScheduleSource.OPAC) {
            return;
        }
        boolean drop = switch (WorldDegradeConfig.opacRemoveClaimAfter()) {
            case FINAL_PASS -> finalPass;
            // Keyed on firstPass, not passIndex == 0: a backlog collapse (feature toggled off/on, or the
            // job slot held) can skip pass 0 so the first fired pass lands on a higher index, and we
            // still want to drop the claim after that first real degradation.
            case FIRST_PASS -> firstPass;
            case SCHEDULE, NEVER -> false;
        };
        if (drop) {
            enqueueUnclaim(level.dimension().location(), chunks);
        }
    }

    // The listener fires when a pass *starts*, but this only queues the unclaim; drainUnclaim runs it
    // later and only while ScheduleService.isIdle(), i.e. after the pass job has finished. That deferral
    // is what makes removeClaimAfter genuinely mean "after the pass finishes" despite firing on start.
    private void enqueueUnclaim(ResourceLocation dimension, LongOpenHashSet chunks) {
        unclaimQueue.computeIfAbsent(dimension, d -> new LongOpenHashSet()).addAll(chunks);
    }

    private void drainUnclaim(MinecraftServer server) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        for (Map.Entry<ResourceLocation, LongOpenHashSet> entry : unclaimQueue.entrySet()) {
            ResourceLocation dimension = entry.getKey();
            for (long chunk : entry.getValue()) {
                int chunkX = ChunkPos.getX(chunk);
                int chunkZ = ChunkPos.getZ(chunk);
                IPlayerChunkClaimAPI claim = claims.get(dimension, chunkX, chunkZ);
                // Only drop it if it is still the expired claim: a chunk re-claimed by a player during
                // the schedule keeps its new claim.
                if (claim != null && SpecialClaimOwners.EXPIRED.equals(claim.getPlayerId())) {
                    claims.unclaim(dimension, chunkX, chunkZ);
                }
            }
        }
        unclaimQueue.clear();
    }
}
