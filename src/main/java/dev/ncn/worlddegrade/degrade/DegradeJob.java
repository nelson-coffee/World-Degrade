package dev.ncn.worlddegrade.degrade;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.compat.RunWork;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.marking.MarkedRegions;
import dev.ncn.worlddegrade.tracking.PlacementTracker;

import java.util.ArrayList;
import dev.ncn.worlddegrade.tracking.TrackedChunkIndex;
import dev.ncn.worlddegrade.undo.UndoManager;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public class DegradeJob {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_INTERVAL = 200;

    private static DegradeJob active;

    private final ServerLevel level;
    private final DegradeArea area;
    private final DegradeChances chances;
    private final LongArrayFIFOQueue chunkQueue;
    private final List<RunWork> extraWork;
    private final List<DegradeEffect> effects;
    private final int totalChunks;
    private final int totalCompatTargets;
    private final int chunksPerTick;
    private final boolean placementTrackingEnabled;
    private final boolean excavationTrackingEnabled;
    @Nullable
    private final UUID operator;
    private final long runSeed;
    private int processedChunks;
    private int changedBlocks;
    private int excavatedCeilings;

    private DegradeJob(ServerLevel level, DegradeArea area, DegradeChances chances,
                       LongArrayFIFOQueue chunkQueue, List<RunWork> extraWork, @Nullable UUID operator) {
        this.level = level;
        this.area = area;
        this.chances = chances;
        this.chunkQueue = chunkQueue;
        this.extraWork = new ArrayList<>(extraWork);
        this.effects = CompatManager.createEffects();
        this.totalChunks = chunkQueue.size();
        int compatTargets = 0;
        for (RunWork work : extraWork) {
            compatTargets += work.targetCount();
        }
        this.totalCompatTargets = compatTargets;
        this.chunksPerTick = WorldDegradeConfig.chunksPerTick();
        this.placementTrackingEnabled = WorldDegradeConfig.placementTrackingEnabled();
        this.excavationTrackingEnabled = WorldDegradeConfig.excavationTrackingEnabled();
        this.operator = operator;
        this.runSeed = level.getRandom().nextLong();
    }

    public static boolean isBusy() {
        return active != null;
    }

    public static boolean isRunning(ServerLevel level) {
        return active != null && active.level == level;
    }

    public int totalChunks() {
        return totalChunks;
    }

    /** Contraptions, trains and ships this run will process on top of {@link #totalChunks()}. */
    public int totalCompatTargets() {
        return totalCompatTargets;
    }

    /**
     * Builds and installs a degradation run over the given area. Callers are responsible for the
     * busy/dimension/empty checks (see {@link DegradeService}); this only gathers the tracked
     * chunks the area touches and starts the job.
     *
     * @return the started job, or {@code null} when the area contains no tracked work.
     */
    @Nullable
    static DegradeJob begin(ServerLevel level, DegradeArea area, DegradeChances chances,
                            boolean saveUndo, @Nullable UUID operator) {
        LongOpenHashSet chunkSet = new LongOpenHashSet();
        for (long packedChunk : TrackedChunkIndex.get(level).allChunks()) {
            if (area.containsChunk(packedChunk)) {
                chunkSet.add(packedChunk);
            }
        }
        for (long packedChunk : MarkedRegions.get(level).regionChunks()) {
            if (area.containsChunk(packedChunk)) {
                chunkSet.add(packedChunk);
            }
        }
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        chunkSet.forEach(queue::enqueue);
        List<RunWork> extraWork = CompatManager.collectRunWork(level, area, chances, operator);
        if (queue.isEmpty() && extraWork.isEmpty()) {
            return null;
        }
        UndoManager.beginRun(level.dimension(), saveUndo);
        active = new DegradeJob(level, area, chances, queue, extraWork, operator);
        LOGGER.info("World Degrade: starting run over {} chunk(s) at level {} in {} (undo={})",
                queue.size(), chances.levelId(), level.dimension().location(), saveUndo);
        active.sendStart(level.getServer());
        return active;
    }

    private void sendStart(MinecraftServer server) {
        sendToOperator(server, Component.translatable("chat.worlddegrade.start",
                totalChunks, chances.levelId(),
                Component.translatable("gui.worlddegrade.tier." + chances.levelId())));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (active != null) {
            active.tick(event.getServer());
        }
        UndoManager.tickRestore(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (active != null) {
            UndoManager.finishRun(event.getServer());
            active = null;
        }
        UndoManager.shutdown();
        CompatManager.onServerStopping();
    }

    private void tick(MinecraftServer server) {
        for (int i = 0; i < chunksPerTick && !chunkQueue.isEmpty(); i++) {
            processChunk(chunkQueue.dequeueLong());
            processedChunks++;
            if (processedChunks % PROGRESS_INTERVAL == 0) {
                sendToOperator(server, Component.translatable("chat.worlddegrade.progress",
                        processedChunks, totalChunks));
            }
        }
        if (chunkQueue.isEmpty() && !extraWork.isEmpty()) {
            RunWork work = extraWork.get(0);
            if (work.tick()) {
                changedBlocks += work.changedBlocks();
                extraWork.remove(0);
            }
        }
        if (chunkQueue.isEmpty() && extraWork.isEmpty()) {
            UndoManager.finishRun(server);
            com.mojang.logging.LogUtils.getLogger().info(
                    "World Degrade: run touched {} dug ceiling(s) across {} chunk(s)",
                    excavatedCeilings, processedChunks);
            sendToOperator(server, Component.translatable("chat.worlddegrade.done",
                    changedBlocks, processedChunks));
            active = null;
        }
    }

    private void processChunk(long packedChunk) {
        LevelChunk chunk = level.getChunk(ChunkPos.getX(packedChunk), ChunkPos.getZ(packedChunk));
        long[] trackedPositions = PlacementTracker.trackedPositions(chunk);
        if (trackedPositions.length == 0
                && dev.ncn.worlddegrade.tracking.ExcavationTracker.excavatedCeilings(chunk).length == 0) {
            TrackedChunkIndex.get(level).removeChunk(chunk.getPos());
        }
        it.unimi.dsi.fastutil.longs.LongOpenHashSet merged =
                MarkedRegions.get(level).collectRegionPositions(level, chunk);
        // Stored tracking data is still read above for index cleanup, but a disabled toggle
        // excludes it from this run without purging it (the toggle can be flipped back on later).
        if (placementTrackingEnabled) {
            for (long tracked : trackedPositions) {
                merged.add(tracked);
            }
        }
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap dugCeilings = excavationTrackingEnabled
                ? collectDugCeilings(chunk)
                : new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        merged.addAll(dugCeilings.keySet());
        excavatedCeilings += dugCeilings.size();
        merged.removeIf((long pos) -> !area.containsBlock(pos));
        if (merged.isEmpty()) {
            return;
        }
        long[] positions = merged.toLongArray();
        DegradeContext context = new DegradeContext(level, chances, UndoManager.current(), positions, runSeed);
        context.setExcavatedCeilings(dugCeilings);
        for (DegradeEffect effect : effects) {
            effect.apply(context);
        }
        chunk.setUnsaved(true);
        changedBlocks += context.changedBlocks();
    }

    private static final int[] LEVEL_DEPTH = {1, 1, 2, 3, 3};

    private static final int LIFETIME_CAP = 3;

    private it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap collectDugCeilings(LevelChunk chunk) {
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap ceilings =
                new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        long[] recorded = dev.ncn.worlddegrade.tracking.ExcavationTracker.excavatedCeilings(chunk);
        if (recorded.length == 0) {
            return ceilings;
        }
        int perRun = LEVEL_DEPTH[Mth.clamp(chances.levelId(), 1, 5) - 1];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (long packed : recorded) {
            BlockPos origin = BlockPos.of(packed);
            for (int consumed = 0; consumed < LIFETIME_CAP; consumed++) {
                cursor.set(origin.getX(), origin.getY() + consumed, origin.getZ());
                if (!level.getBlockState(cursor).blocksMotion()) {
                    continue;
                }
                int allowance = Math.min(perRun, LIFETIME_CAP - consumed);
                if (allowance > 0) {
                    long key = cursor.asLong();
                    ceilings.put(key, Math.max(ceilings.get(key), allowance));
                }
                break;
            }
        }
        return ceilings;
    }

    private void sendToOperator(MinecraftServer server, Component message) {
        if (operator == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(operator);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }
}
