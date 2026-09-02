package dev.ncn.worlddegrade.degrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedItemsTest {

    private static NestedItems.Budget budget(int limit) {
        return new NestedItems.Budget(limit, "test");
    }

    @Test
    void spendsExactlyTheLimit() {
        NestedItems.Budget budget = budget(3);
        assertTrue(budget.spend());
        assertTrue(budget.spend());
        assertTrue(budget.spend());
        assertFalse(budget.spend(), "fourth visit exceeds a limit of three");
    }

    @Test
    void exhaustionLatchesOnce() {
        NestedItems.Budget budget = budget(1);
        assertFalse(budget.exhausted());
        budget.spend();
        assertFalse(budget.exhausted(), "spending the last unit does not itself exhaust");
        budget.spend();
        assertTrue(budget.exhausted());
        budget.spend();
        assertTrue(budget.exhausted(), "stays exhausted");
    }

    @Test
    void zeroLimitRefusesImmediately() {
        NestedItems.Budget budget = budget(0);
        assertFalse(budget.spend());
        assertTrue(budget.exhausted());
    }

    @Test
    void changeCountTracksRecordedChanges() {
        NestedItems.Budget budget = budget(10);
        assertEquals(0, budget.changeCount());
        budget.recordChange();
        budget.recordChange();
        assertEquals(2, budget.changeCount());
    }

    @Test
    void changesAreIndependentOfBudget() {
        NestedItems.Budget budget = budget(1);
        budget.recordChange();
        budget.spend();
        budget.spend();
        assertTrue(budget.exhausted());
        assertEquals(1, budget.changeCount(), "exhaustion does not discard recorded changes");
    }
}
