package dev.ncn.worlddegrade.schedule;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.DegradeJob;
import dev.ncn.worlddegrade.undo.UndoManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public entry point for the degradation schedule (#5), the sibling of
 * {@link dev.ncn.worlddegrade.degrade.DegradeService} for the timed multi-pass feature. Other mods
 * (OPAC #6, Create schematicannon habitation, a future claim mod) integrate through here rather than
 * touching {@link ScheduledDegradations} directly.
 *
 * <p>Undo is deliberately absent from this surface: scheduled passes run unattended over time and any
 * snapshot would be overwritten before it could be used, so scheduled degradation never captures one.
 * That is enforced structurally — the scheduler is the only caller of the degradation pipeline and it
 * always passes {@code saveUndo = false} — not by a flag exposed here.
 */
public final class ScheduleService {

    /**
     * Upper bound on the chunks a single schedule may cover. The command applies its own limit before
     * reaching here; this is the backstop for API callers so a mistaken million-chunk request cannot
     * grow the reverse map and the persisted {@code long[]} without bound.
     */
    public static final int MAX_CHUNKS = 10_000;

    private static final Logger LOGGER = LogUtils.getLogger();
    // Copy-on-write because listeners register once at setup and are only iterated on the scheduler
    // tick; registration is rare, iteration is hot.
    private static final List<SchedulePassListener> PASS_LISTENERS = new CopyOnWriteArrayList<>();

    // Set by the OPAC compat (#6) when the mod is present, so the debug command can drive the real
    // expiration pipeline (batcher -> OPAC-sourced schedule -> unclaim) without OPAC types leaking into
    // core. Null when OPAC is not installed.
    private static volatile OpacExpirationSimulator opacSimulator;

    private ScheduleService() {
    }

    /**
     * Hook the OPAC compat registers so operators can trigger the claim-expiration path on demand.
     * OPAC only expires claims of players inactive for hours, which is impractical to reproduce in a
     * dev/single-player session, so the {@code /degrade opac simulate} command routes through this to
     * feed chunks into the exact same batcher the real OPAC callback uses.
     */
    @FunctionalInterface
    public interface OpacExpirationSimulator {
        /**
         * Queues the chunks as if their OPAC claim had just expired; returns how many were queued. When
         * {@code expireClaims} is true the chunks are first set to OPAC's expired-claim owner (exactly
         * as a real expiration does), so the post-pass unclaim genuinely removes them; otherwise the
         * claims are left untouched and only the degradation runs.
         */
        int simulate(ServerLevel level, LongOpenHashSet chunks, boolean expireClaims);
    }

    public static void setOpacSimulator(OpacExpirationSimulator simulator) {
        opacSimulator = simulator;
    }

    /** Whether the OPAC integration is present and can simulate an expiration. */
    public static boolean opacSimulationAvailable() {
        return opacSimulator != null;
    }

    /**
     * Feeds {@code chunks} into the OPAC expiration pipeline as if their claim had just expired. Returns
     * the number of chunks queued, or {@code -1} when the OPAC integration is not installed.
     */
    public static int simulateOpacExpiration(ServerLevel level, LongOpenHashSet chunks, boolean expireClaims) {
        OpacExpirationSimulator simulator = opacSimulator;
        return simulator == null ? -1 : simulator.simulate(level, chunks, expireClaims);
    }

    /**
     * Registers a listener notified whenever a schedule pass fires. Used by the OPAC compat (#6) to
     * drop the expired claim after the configured pass; other integrations may follow. Registration is
     * process-wide and expected once at setup, so there is no deregistration.
     */
    public static void onPassFired(SchedulePassListener listener) {
        PASS_LISTENERS.add(listener);
    }

    static void firePassListeners(ServerLevel level, ScheduleSource source, LongOpenHashSet chunks,
                                  int passIndex, boolean firstPass, boolean finalPass) {
        for (SchedulePassListener listener : PASS_LISTENERS) {
            try {
                listener.onPassFired(level, source, chunks, passIndex, firstPass, finalPass);
            } catch (Throwable t) {
                LOGGER.error("World Degrade: a schedule pass listener failed", t);
            }
        }
    }

    /**
     * Whether the single degradation job slot is free right now, so an integration's deferred
     * side-effect (OPAC's unclaim, #6) can run without racing an in-flight pass or undo. Kept here so
     * the OPAC compat never has to import {@link DegradeJob}/{@link UndoManager} — those imports would
     * silently break the "OPAC runs capture no undo" acceptance criterion if they crept in.
     */
    public static boolean isIdle() {
        return !DegradeJob.isBusy() && !UndoManager.isRestoring();
    }

    /**
     * Starts a schedule over the given chunk columns, using the pass table from the server config.
     * See {@link ScheduleResult} for the rejection reasons.
     */
    public static ScheduleResult schedule(ServerLevel level, Collection<ChunkPos> chunks) {
        LongOpenHashSet packed = new LongOpenHashSet(chunks.size());
        for (ChunkPos chunk : chunks) {
            packed.add(chunk.toLong());
        }
        return schedule(level, packed);
    }

    /**
     * Long-packed overload that avoids re-boxing chunk positions; the caller-owned set is never
     * retained (the store copies only the chunks it actually claims).
     */
    public static ScheduleResult schedule(ServerLevel level, LongOpenHashSet packedChunks) {
        return schedule(level, packedChunks, ScheduleSource.GLOBAL);
    }

    /**
     * Source-aware overload. The pass table checked for emptiness — and later used to run the schedule
     * — is the one that source resolves to, so OPAC (#6) is gated on its own table, not the global one.
     * The master switch {@link WorldDegradeConfig#scheduleEnabled()} still gates every source.
     */
    public static ScheduleResult schedule(ServerLevel level, LongOpenHashSet packedChunks,
                                          ScheduleSource source) {
        if (!WorldDegradeConfig.scheduleEnabled() || WorldDegradeConfig.schedule(source).isEmpty()) {
            return ScheduleResult.rejected(ScheduleResult.Status.DISABLED);
        }
        if (WorldDegradeConfig.isDimensionDisabled(level)) {
            return ScheduleResult.rejected(ScheduleResult.Status.DIMENSION_DISABLED);
        }
        if (packedChunks.isEmpty()) {
            return ScheduleResult.rejected(ScheduleResult.Status.EMPTY_AREA);
        }
        if (packedChunks.size() > MAX_CHUNKS) {
            return ScheduleResult.rejected(ScheduleResult.Status.TOO_LARGE);
        }
        ScheduledDegradations store = ScheduledDegradations.get(level);
        int id = store.schedule(packedChunks, level.getGameTime(), source);
        if (id < 0) {
            return ScheduleResult.rejected(ScheduleResult.Status.ALREADY_SCHEDULED);
        }
        ScheduledDegradations.Entry entry = store.entry(id);
        return ScheduleResult.created(id, entry == null ? 0 : entry.chunkCount());
    }

    /**
     * Signals "something is building here." Counts one unit of use against the schedule covering the
     * position; when the configured {@code releaseBlockThreshold} is reached the whole schedule is
     * cancelled, since an inhabited area should not keep degrading. A threshold of 0 switches the
     * check off and this becomes a no-op. Returns {@code true} only when a schedule was cancelled.
     */
    public static boolean markInUse(ServerLevel level, BlockPos pos) {
        return markInUse(level, new ChunkPos(pos));
    }

    public static boolean markInUse(ServerLevel level, ChunkPos chunk) {
        // Checked before touching the data storage so a disabled feature costs one comparison on the
        // per-placement path and never creates a SavedData.
        if (!habitationCountingEnabled()) {
            return false;
        }
        ScheduledDegradations store = ScheduledDegradations.existing(level);
        return store != null && store.markInUse(chunk.toLong(), WorldDegradeConfig::threshold);
    }

    /**
     * Whether {@link #markInUse} can do anything at all right now. Integrations that have to keep
     * per-object bookkeeping to call it exactly once (the Create schematicannon does) should check this
     * first and skip that bookkeeping entirely when it returns false.
     */
    public static boolean habitationCountingEnabled() {
        // Either threshold being positive is enough: a per-source counter must still tick even when the
        // other source's check is off, or the Create schematicannon tracker would skip its bookkeeping
        // and never count blocks into an OPAC schedule (and vice versa).
        return WorldDegradeConfig.scheduleEnabled()
                && (WorldDegradeConfig.releaseBlockThreshold() > 0
                || WorldDegradeConfig.opacReleaseBlockThreshold() > 0);
    }

    /** Whether a schedule currently covers the chunk holding this position. Side-effect free. */
    public static boolean isScheduled(ServerLevel level, BlockPos pos) {
        return isScheduled(level, new ChunkPos(pos).toLong());
    }

    /** Long-packed chunk overload of {@link #isScheduled(ServerLevel, BlockPos)}. */
    public static boolean isScheduled(ServerLevel level, long packedChunk) {
        if (!WorldDegradeConfig.scheduleEnabled()) {
            return false;
        }
        ScheduledDegradations store = ScheduledDegradations.existing(level);
        return store != null && store.isScheduled(packedChunk);
    }

    /** Read-only view of the schedules active in this dimension. */
    public static Collection<ScheduledDegradations.Entry> activeSchedules(ServerLevel level) {
        ScheduledDegradations store = ScheduledDegradations.existing(level);
        return store == null ? List.of() : store.entries();
    }

    /** Force-cancels a schedule by id, ignoring any usage threshold. */
    public static boolean cancel(ServerLevel level, int scheduleId) {
        ScheduledDegradations store = ScheduledDegradations.existing(level);
        return store != null && store.removeById(scheduleId);
    }

    /** Cancels every schedule in this dimension; returns how many were removed. */
    public static int cancelAll(ServerLevel level) {
        ScheduledDegradations store = ScheduledDegradations.existing(level);
        return store == null ? 0 : store.clear();
    }
}
