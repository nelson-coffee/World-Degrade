package dev.ncn.worlddegrade.undo;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@code saveUndo=false} opt-out that automated callers (#5/#6) rely on: a discarding
 * snapshot must never record and must report itself as non-recording so {@code UndoManager} can
 * skip the disk write and leave a prior manual undo intact.
 */
class UndoSnapshotTest {

    @Test
    void normalSnapshotIsRecording() {
        assertTrue(new UndoSnapshot(Level.OVERWORLD).isRecording());
    }

    @Test
    void discardingSnapshotIsNotRecording() {
        assertFalse(UndoSnapshot.discarding(Level.OVERWORLD).isRecording());
    }

    @Test
    void discardingSnapshotSwallowsRecords() {
        UndoSnapshot snapshot = UndoSnapshot.discarding(Level.OVERWORLD);
        // record() returns before touching the level, so null args are safe for a discarding one.
        snapshot.record(null, null);
        assertTrue(snapshot.isEmpty());
    }
}
