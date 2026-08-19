package dev.ncn.worlddegrade.schedule;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledDegradationsTest {

    private static long chunk(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private static LongOpenHashSet set(long... chunks) {
        LongOpenHashSet result = new LongOpenHashSet();
        for (long chunk : chunks) {
            result.add(chunk);
        }
        return result;
    }

    private static DegradeSchedule schedule() {
        return DegradeSchedule.fromConfig(List.of(7, 30, 60), List.of(1, 3, 5));
    }

    @Test
    void scheduleRegistersEveryChunkAndReturnsAnId() {
        ScheduledDegradations store = new ScheduledDegradations();
        int id = store.schedule(set(chunk(0, 0), chunk(1, 0), chunk(0, 1)), 1_000L);
        assertTrue(id > 0);
        assertTrue(store.isScheduled(chunk(0, 0)));
        assertTrue(store.isScheduled(chunk(0, 1)));
        assertEquals(1, store.entries().size());
    }

    @Test
    void overlappingChunksStayWithTheOriginalEntry() {
        ScheduledDegradations store = new ScheduledDegradations();
        int first = store.schedule(set(chunk(0, 0), chunk(1, 0), chunk(2, 0)), 0L);
        int second = store.schedule(set(chunk(2, 0), chunk(3, 0)), 0L);
        assertTrue(second > first);
        // The second entry only owns chunk (3,0); (2,0) remained with the first.
        ScheduledDegradations.Entry secondEntry = byId(store, second);
        assertEquals(1, secondEntry.chunkCount());
    }

    @Test
    void scheduleReturnsMinusOneWhenEverythingIsAlreadyOwned() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L);
        assertEquals(-1, store.schedule(set(chunk(0, 0)), 0L));
    }

    @Test
    void placingInsideAnEntryReleasesTheWholeEntry() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0), chunk(1, 0), chunk(2, 0)), 0L);
        assertTrue(store.markInUse(chunk(1, 0), 1));
        assertFalse(store.isScheduled(chunk(0, 0)));
        assertFalse(store.isScheduled(chunk(2, 0)));
        assertTrue(store.entries().isEmpty());
    }

    @Test
    void releaseOnlyAffectsTheOwningEntry() {
        ScheduledDegradations store = new ScheduledDegradations();
        int keep = store.schedule(set(chunk(10, 10)), 0L);
        store.schedule(set(chunk(0, 0)), 0L);
        assertTrue(store.markInUse(chunk(0, 0), 1));
        assertTrue(store.isScheduled(chunk(10, 10)));
        assertEquals(1, store.entries().size());
        assertEquals(keep, store.entries().iterator().next().id());
    }

    @Test
    void markingAnUnscheduledChunkDoesNothing() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L);
        assertFalse(store.markInUse(chunk(99, 99), 1));
        assertEquals(1, store.entries().size());
    }

    @Test
    void thresholdCountsPlacementsBeforeReleasing() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0), chunk(1, 0)), 0L);
        assertFalse(store.markInUse(chunk(0, 0), 3));
        assertFalse(store.markInUse(chunk(1, 0), 3));
        assertTrue(store.markInUse(chunk(0, 0), 3));
        assertTrue(store.entries().isEmpty());
    }

    @Test
    void aThresholdOfZeroDisablesTheInhabitedCheck() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L);
        assertFalse(store.markInUse(chunk(0, 0), 0));
        assertFalse(store.markInUse(chunk(0, 0), 0));
        assertEquals(1, store.entries().size());
        // Nothing was counted either, so raising the threshold later starts from zero.
        assertEquals(0, store.entries().iterator().next().uses());
    }

    @Test
    void absorbPauseShiftsTriggersByTheUncountedGap() {
        ScheduledDegradations store = new ScheduledDegradations();
        DegradeSchedule schedule = schedule();
        store.schedule(set(chunk(0, 0)), 0L);
        store.absorbPause(0L, 20); // establish the reference point
        // The scheduler was off for 10 000 ticks, so only the 20-tick check interval really elapsed.
        long paused = store.absorbPause(10_020L, 20);
        assertEquals(10_000L, paused);
        assertEquals(10_000L, store.entries().iterator().next().triggerGameTime());
        // The first pass is therefore still exactly its full delay away, measured from the new trigger.
        long due = 10_000L + 7 * DegradeSchedule.MINUTE_TICKS;
        assertNull(store.pollDue(due - 1, schedule));
        assertNotNull(store.pollDue(due, schedule));
    }

    @Test
    void absorbPauseIgnoresNormalCheckIntervals() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 100L);
        store.absorbPause(500L, 20);
        assertEquals(0L, store.absorbPause(520L, 20));
        assertEquals(100L, store.entries().iterator().next().triggerGameTime());
    }

    @Test
    void absorbPauseDoesNothingOnTheFirstCheckAfterLoad() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L);
        // No reference point yet: a large game time must not be mistaken for a pause.
        assertEquals(0L, store.absorbPause(5_000_000L, 20));
        assertEquals(0L, store.entries().iterator().next().triggerGameTime());
    }

    @Test
    void advancingPastTheFinalPassRemovesTheEntry() {
        ScheduledDegradations store = new ScheduledDegradations();
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(1, 2), List.of(1, 2));
        int id = store.schedule(set(chunk(0, 0)), 0L);
        store.advance(byId(store, id), schedule);
        assertEquals(1, store.entries().size());
        store.advance(byId(store, id), schedule);
        assertTrue(store.entries().isEmpty());
    }

    @Test
    void advancingResetsTheUsesCounter() {
        ScheduledDegradations store = new ScheduledDegradations();
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(1, 2), List.of(1, 2));
        int id = store.schedule(set(chunk(0, 0)), 0L);
        assertFalse(store.markInUse(chunk(0, 0), 3)); // uses = 1, below threshold
        assertEquals(1, byId(store, id).uses());
        store.advance(byId(store, id), schedule);
        assertEquals(0, byId(store, id).uses());
    }

    @Test
    void advanceToSkipsStraightToTheGivenPass() {
        ScheduledDegradations store = new ScheduledDegradations();
        DegradeSchedule schedule = DegradeSchedule.fromConfig(List.of(1, 2, 3), List.of(1, 2, 3));
        int id = store.schedule(set(chunk(0, 0)), 0L);
        store.advanceTo(byId(store, id), 1, schedule); // fired pass index 1 -> next is index 2
        assertEquals(2, byId(store, id).nextPass());
        store.advanceTo(byId(store, id), 2, schedule); // fired the last pass -> entry removed
        assertTrue(store.entries().isEmpty());
    }

    @Test
    void pollDueReturnsAnEntryOnlyOnceItsPassIsDue() {
        ScheduledDegradations store = new ScheduledDegradations();
        DegradeSchedule schedule = schedule();
        store.schedule(set(chunk(0, 0)), 0L);
        long firstPassDue = 7 * DegradeSchedule.MINUTE_TICKS;
        assertNull(store.pollDue(firstPassDue - 1, schedule));
        assertNotNull(store.pollDue(firstPassDue, schedule));
    }

    @Test
    void pollDueReturnsTheMostOverdueEntry() {
        ScheduledDegradations store = new ScheduledDegradations();
        DegradeSchedule schedule = schedule();
        store.schedule(set(chunk(0, 0)), 5_000L);
        int oldest = store.schedule(set(chunk(50, 50)), 0L);
        store.schedule(set(chunk(80, 80)), 2_000L);
        long now = 5_000L + 7 * DegradeSchedule.MINUTE_TICKS; // all three are due
        assertEquals(oldest, store.pollDue(now, schedule).id());
    }

    @Test
    void pollDueClampsAFutureTriggerToNow() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 1_000_000L);
        // now is well before the trigger; it should be clamped, and the first pass is then not yet due.
        assertNull(store.pollDue(0L, schedule()));
        ScheduledDegradations.Entry entry = store.entries().iterator().next();
        assertEquals(0L, entry.triggerGameTime());
    }

    @Test
    void nbtRoundTripPreservesEntriesAndMapping() {
        ScheduledDegradations original = new ScheduledDegradations();
        int a = original.schedule(set(chunk(0, 0), chunk(1, 0)), 5_000L);
        int b = original.schedule(set(chunk(50, 50)), 9_000L);
        original.advance(byId(original, b), schedule()); // b now on pass 1

        CompoundTag tag = original.save(new CompoundTag(), null);
        ScheduledDegradations reloaded = ScheduledDegradations.load(tag, null);

        assertEquals(2, reloaded.entries().size());
        assertTrue(reloaded.isScheduled(chunk(0, 0)));
        assertTrue(reloaded.isScheduled(chunk(1, 0)));
        assertTrue(reloaded.isScheduled(chunk(50, 50)));
        assertEquals(5_000L, byId(reloaded, a).triggerGameTime());
        assertEquals(0, byId(reloaded, a).nextPass());
        assertEquals(1, byId(reloaded, b).nextPass());
        // New schedules must not reuse a persisted id.
        int c = reloaded.schedule(set(chunk(-1, -1)), 0L);
        assertTrue(c > a && c > b);
    }

    @Test
    void thePauseReferencePointSurvivesAReload() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 1_000L);
        store.absorbPause(2_000L, 20);

        ScheduledDegradations reloaded = ScheduledDegradations.load(store.save(new CompoundTag(), null), null);
        // Game time does not advance while the server is off, so a restart must not look like a pause.
        assertEquals(0L, reloaded.absorbPause(2_020L, 20));
        assertEquals(1_000L, reloaded.entries().iterator().next().triggerGameTime());
    }

    @Test
    void movingThePauseReferencePointMarksTheStoreDirty() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 1_000L);
        store.absorbPause(1_000L, 20);
        store.setDirty(false); // as if the world had just been saved
        store.absorbPause(1_020L, 20);
        // A quiet check moves the reference point and nothing else. If that does not dirty the store
        // it is never written, so the persisted point stays frozen while game time runs on and the
        // next restart discounts all of that uptime as a pause.
        assertTrue(store.isDirty());
    }

    @Test
    void anIdleDimensionDoesNotDirtyTheStore() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.absorbPause(5_000L, 20);
        // No schedules means no clock to preserve, so a per-second check must not keep the store dirty.
        assertFalse(store.isDirty());
    }

    @Test
    void hoursOfQuietUptimeAreNotDiscountedAfterAReload() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L);
        // Nothing but scheduler checks: no placements, no passes, nothing else to dirty the store.
        for (long now = 0L; now <= 100_000L; now += 20L) {
            assertEquals(0L, store.absorbPause(now, 20));
        }
        ScheduledDegradations reloaded =
                ScheduledDegradations.load(store.save(new CompoundTag(), null), null);
        assertEquals(0L, reloaded.absorbPause(100_020L, 20));
        assertEquals(0L, reloaded.entries().iterator().next().triggerGameTime());
    }

    @Test
    void entriesWithNoChunksAreSkippedOnLoad() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L);
        CompoundTag tag = store.save(new CompoundTag(), null);
        // Corrupt the single entry to have an empty chunk array.
        tag.getList("entries", Tag.TAG_COMPOUND).getCompound(0)
                .putLongArray("chunks", new long[0]);
        ScheduledDegradations reloaded = ScheduledDegradations.load(tag, null);
        assertTrue(reloaded.entries().isEmpty());
    }

    @Test
    void duplicateAndNonPositiveIdsAreDroppedOnLoad() {
        ScheduledDegradations store = new ScheduledDegradations();
        store.schedule(set(chunk(0, 0)), 0L); // id 1
        CompoundTag tag = store.save(new CompoundTag(), null);
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        // A second entry reusing id 1 would orphan its chunks in the reverse map.
        CompoundTag duplicate = list.getCompound(0).copy();
        duplicate.putLongArray("chunks", new long[]{chunk(5, 5)});
        list.add(duplicate);
        // A non-positive id would collide with the chunkToEntry -1 sentinel.
        CompoundTag zeroId = list.getCompound(0).copy();
        zeroId.putInt("id", 0);
        zeroId.putLongArray("chunks", new long[]{chunk(9, 9)});
        list.add(zeroId);

        ScheduledDegradations reloaded = ScheduledDegradations.load(tag, null);
        assertEquals(1, reloaded.entries().size());
        assertTrue(reloaded.isScheduled(chunk(0, 0)));
        assertFalse(reloaded.isScheduled(chunk(5, 5)));
        assertFalse(reloaded.isScheduled(chunk(9, 9)));
    }

    private static ScheduledDegradations.Entry byId(ScheduledDegradations store, int id) {
        for (ScheduledDegradations.Entry entry : store.entries()) {
            if (entry.id() == id) {
                return entry;
            }
        }
        throw new AssertionError("no entry with id " + id);
    }
}
