package dev.ncn.worlddegrade.schedule;

import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.List;

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

    private ScheduleService() {
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
        if (!WorldDegradeConfig.scheduleEnabled() || WorldDegradeConfig.schedule().isEmpty()) {
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
        int id = store.schedule(packedChunks, level.getGameTime());
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
        return store != null
                && store.markInUse(chunk.toLong(), WorldDegradeConfig.releaseBlockThreshold());
    }

    /**
     * Whether {@link #markInUse} can do anything at all right now. Integrations that have to keep
     * per-object bookkeeping to call it exactly once (the Create schematicannon does) should check this
     * first and skip that bookkeeping entirely when it returns false.
     */
    public static boolean habitationCountingEnabled() {
        return WorldDegradeConfig.scheduleEnabled() && WorldDegradeConfig.releaseBlockThreshold() > 0;
    }

    /** Whether a schedule currently covers the chunk holding this position. Side-effect free. */
    public static boolean isScheduled(ServerLevel level, BlockPos pos) {
        if (!WorldDegradeConfig.scheduleEnabled()) {
            return false;
        }
        ScheduledDegradations store = ScheduledDegradations.existing(level);
        return store != null && store.isScheduled(new ChunkPos(pos).toLong());
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
