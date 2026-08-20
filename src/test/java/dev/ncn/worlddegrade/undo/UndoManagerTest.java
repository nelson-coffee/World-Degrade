package dev.ncn.worlddegrade.undo;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@code saveUndo} wiring that automated callers (#5/#6) depend on. A non-recording run
 * must never reach the disk, which is what lets these tests pass a {@code null} server: touching it
 * would be the very regression they guard against.
 */
class UndoManagerTest {

    @AfterEach
    void reset() {
        UndoManager.shutdown();
    }

    @Test
    void manualRunRecords() {
        UndoManager.beginRun(Level.OVERWORLD, true);
        assertNotNull(UndoManager.current());
        assertTrue(UndoManager.current().isRecording());
    }

    @Test
    void automatedRunDoesNotRecord() {
        UndoManager.beginRun(Level.OVERWORLD, false);
        assertNotNull(UndoManager.current());
        assertFalse(UndoManager.current().isRecording());
    }

    // The disk write is the only thing that needs the server. Passing null proves an opted-out run
    // takes the early return instead, so it cannot overwrite an earlier manual run's snapshot file.
    @Test
    void finishingAnOptedOutRunNeverTouchesTheServer() {
        UndoManager.beginRun(Level.OVERWORLD, false);
        UndoManager.finishRun(null);
        assertNull(UndoManager.current());
    }

    @Test
    void finishingWithNoRunIsANoop() {
        UndoManager.finishRun(null);
        assertNull(UndoManager.current());
    }

    // Back-to-back automated runs are the #5 schedule's steady state; none may start recording.
    @Test
    void repeatedAutomatedRunsStayNonRecording() {
        for (int i = 0; i < 3; i++) {
            UndoManager.beginRun(Level.OVERWORLD, false);
            assertFalse(UndoManager.current().isRecording());
            UndoManager.finishRun(null);
            assertNull(UndoManager.current());
        }
    }
}
