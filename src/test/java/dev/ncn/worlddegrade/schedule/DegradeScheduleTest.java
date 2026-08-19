package dev.ncn.worlddegrade.schedule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradeScheduleTest {

    private static final long MINUTE = DegradeSchedule.MINUTE_TICKS;

    @Test
    void pairsDelaysWithLevelsInOrder() {
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(7, 30, 60), List.of(1, 3, 5));
        assertEquals(3, schedule.passes().size());
        assertEquals(new DegradeSchedule.Pass(7 * MINUTE, 1), schedule.passes().get(0));
        assertEquals(new DegradeSchedule.Pass(30 * MINUTE, 3), schedule.passes().get(1));
        assertEquals(new DegradeSchedule.Pass(60 * MINUTE, 5), schedule.passes().get(2));
    }

    @Test
    void mismatchedLengthsTruncateToTheShorterList() {
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(7, 30, 60), List.of(1, 3));
        assertEquals(2, schedule.passes().size());
        assertEquals(7 * MINUTE, schedule.passes().get(0).delayTicks());
        assertEquals(30 * MINUTE, schedule.passes().get(1).delayTicks());
    }

    @Test
    void outOfOrderDelaysAreSortedButKeepTheirPairedLevel() {
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(30, 7, 60), List.of(1, 2, 3));
        assertEquals(3, schedule.passes().size());
        // delay 7 was paired with level 2, delay 30 with level 1, delay 60 with level 3.
        assertEquals(new DegradeSchedule.Pass(7 * MINUTE, 2), schedule.passes().get(0));
        assertEquals(new DegradeSchedule.Pass(30 * MINUTE, 1), schedule.passes().get(1));
        assertEquals(new DegradeSchedule.Pass(60 * MINUTE, 3), schedule.passes().get(2));
    }

    @Test
    void levelsAreClampedToOneThroughFive() {
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(7, 30), List.of(0, 9));
        assertEquals(1, schedule.passes().get(0).levelId());
        assertEquals(5, schedule.passes().get(1).levelId());
    }

    @Test
    void nonPositiveDelaysAreDropped() {
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(0, -5, 7), List.of(1, 2, 3));
        assertEquals(1, schedule.passes().size());
        // Only the delay-7 / level-3 pair survives.
        assertEquals(new DegradeSchedule.Pass(7 * MINUTE, 3), schedule.passes().get(0));
    }

    @Test
    void emptyListsYieldAnEmptySchedule() {
        assertTrue(DegradeSchedule.fromConfig(List.of(), List.of()).isEmpty());
    }

    @Test
    void latestDuePassCollapsesAnOverdueBacklogToTheHighestPass() {
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(7, 30, 60), List.of(1, 3, 5));
        // Only the first pass is due.
        assertEquals(0, schedule.latestDuePass(0L, 0, 7 * MINUTE));
        // Passes 0 and 1 are due but not 2.
        assertEquals(1, schedule.latestDuePass(0L, 0, 30 * MINUTE));
        // Everything is overdue: jump straight to the last pass rather than firing 0, 1, 2 in a burst.
        assertEquals(2, schedule.latestDuePass(0L, 0, 1_000 * MINUTE));
        // Never rewinds below the pass the entry is already on.
        assertEquals(2, schedule.latestDuePass(0L, 2, 1_000 * MINUTE));
    }
}
