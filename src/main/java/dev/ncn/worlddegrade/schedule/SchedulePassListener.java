package dev.ncn.worlddegrade.schedule;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;

/**
 * Notified when a schedule pass has just <em>started</em> (not finished). Lets an integration react to
 * its own schedules progressing without the scheduler depending on that integration's types — the OPAC
 * compat (#6) uses it to drop the expired claim once the configured pass has run. A side-effect that
 * must land only after the pass job completes (as OPAC's unclaim does) should be deferred and run while
 * {@link ScheduleService#isIdle()} rather than executed inline here.
 *
 * <p>The chunk set is a defensive copy taken before the entry is advanced, so it is safe to retain.
 * {@code passIndex} is the (0-based) index into the entry's own pass table that just fired, and
 * {@code finalPass} is true when it was the last one, i.e. the schedule is now complete. Only the
 * normal "a pass fired" path calls this; cancellation, dimension-disable pruning and shrunk-table
 * pruning deliberately do not, so a listener can treat every callback as real progress.
 *
 * <p>{@code firstPass} is true when no pass of this schedule had fired before this one. It is a
 * separate signal from {@code passIndex == 0} on purpose: a backlog collapse can make the first
 * <em>fired</em> pass land on {@code passIndex >= 1} (intermediate overdue passes are skipped), so a
 * listener that wants "the first time this schedule degraded" must use this flag, not the index.
 */
@FunctionalInterface
public interface SchedulePassListener {
    void onPassFired(ServerLevel level, ScheduleSource source, LongOpenHashSet chunks,
                     int passIndex, boolean firstPass, boolean finalPass);
}
