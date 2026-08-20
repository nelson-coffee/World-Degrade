package dev.ncn.worlddegrade.degrade;

import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.undo.UndoManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Player-independent entry point for degradation. The GUI, the {@code /degrade area} command, the
 * degradation schedule (#5) and the OPAC compat module (#6) all funnel through here instead of
 * touching {@link DegradeJob} directly.
 *
 * <p>Every overload validates (area non-empty, dimension not disabled, nothing already running) and
 * returns a {@link DegradeResult} saying what happened. This is the single place those checks live —
 * callers map {@link DegradeResult#status()} to whatever feedback suits them rather than repeating
 * the validation to work out which message to show.
 *
 * <p>{@code saveUndo} defaults to {@code false}: undo snapshots are only worth their storage cost
 * for intentional, one-off manual runs. Automated callers should leave it off.
 */
public final class DegradeService {

    private DegradeService() {
    }

    /** Degrade tracked blocks within the given box (no undo, no operator). */
    public static DegradeResult start(ServerLevel level, BoundingBox area, DegradeChances chances) {
        return start(level, new DegradeArea.Box(area), chances, false, null);
    }

    /** Degrade tracked blocks within the given box at a preset severity. */
    public static DegradeResult start(ServerLevel level, BoundingBox area, DegradeLevel severity) {
        return start(level, area, DegradeChances.of(severity));
    }

    /** Degrade the given chunk columns (OPAC claims are chunk-aligned). */
    public static DegradeResult start(ServerLevel level, Collection<ChunkPos> chunks, DegradeLevel severity) {
        return start(level, chunks, severity, false);
    }

    /** Degrade the given chunk columns, optionally capturing an undo snapshot. */
    public static DegradeResult start(ServerLevel level, Collection<ChunkPos> chunks,
                                      DegradeLevel severity, boolean saveUndo) {
        return start(level, DegradeArea.ofChunks(chunks), DegradeChances.of(severity), saveUndo, null);
    }

    /**
     * Canonical entry point. Validates, then delegates to {@link DegradeJob#begin}.
     *
     * @param operator the player to report progress to, or {@code null} for server-driven runs.
     */
    public static DegradeResult start(ServerLevel level, DegradeArea area, DegradeChances chances,
                                      boolean saveUndo, @Nullable UUID operator) {
        if (DegradeJob.isBusy() || UndoManager.isRestoring()) {
            return DegradeResult.rejected(DegradeResult.Status.BUSY);
        }
        if (WorldDegradeConfig.isDimensionDisabled(level)) {
            return DegradeResult.rejected(DegradeResult.Status.DIMENSION_DISABLED);
        }
        // An empty claim list is a normal outcome for #5/#6 and must stop here: the compat scan hull
        // for an empty chunk set is a degenerate box at the origin, which still intersects anything
        // straddling x=0/z=0 and would wreck a contraption that nothing actually selected.
        if (area.isEmpty()) {
            return DegradeResult.rejected(DegradeResult.Status.EMPTY_AREA);
        }
        DegradeJob job = DegradeJob.begin(level, area, chances, saveUndo, operator);
        return job == null
                ? DegradeResult.rejected(DegradeResult.Status.NOTHING_FOUND)
                : DegradeResult.started(job);
    }

    public static boolean isRunning(ServerLevel level) {
        return DegradeJob.isRunning(level);
    }
}
