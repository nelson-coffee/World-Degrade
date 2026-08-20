package dev.ncn.worlddegrade.schedule;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ordered table of degradation passes a schedule runs, derived from the two parallel config
 * lists {@code passDelays} / {@code passLevels}. Pure and immutable so it can be unit-tested without
 * a server and cached in {@link dev.ncn.worlddegrade.config.WorldDegradeConfig}.
 *
 * <p>Each {@link Pass} is a delay measured from the moment a schedule is triggered (not cumulative
 * between passes) and a severity level. Delays are expressed in <em>real-life minutes</em> of server
 * runtime, where one minute is {@link #MINUTE_TICKS} ticks at the normal 20 tps — timing therefore
 * advances only while the server is running (and slows if the server is lagging), satisfying the
 * issue's "server runtime, not wall clock" requirement.
 */
public record DegradeSchedule(List<Pass> passes) {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ticks in one real-life minute of server runtime at the normal 20 ticks per second. */
    public static final long MINUTE_TICKS = 1_200L;

    public record Pass(long delayTicks, int levelId) {
    }

    public boolean isEmpty() {
        return passes.isEmpty();
    }

    /**
     * The furthest pass index at or after {@code fromPass} whose delay is already due at {@code now},
     * assuming {@code fromPass} itself is due. When the scheduler falls behind (feature toggled off for
     * a while, a long manual run holding the single job slot, chunks never loaded), several passes can
     * come due at once; collapsing them to the single highest-overdue pass keeps the end state correct
     * without firing every intermediate level in a burst seconds apart.
     */
    public int latestDuePass(long triggerGameTime, int fromPass, long now) {
        int pass = fromPass;
        while (pass + 1 < passes.size()
                && now >= triggerGameTime + passes.get(pass + 1).delayTicks()) {
            pass++;
        }
        return pass;
    }

    /**
     * Builds a normalized schedule from the raw config lists. The two-parallel-lists shape is fragile,
     * so this is deliberately defensive:
     * <ul>
     *   <li>pairs are matched by index and truncated to the shorter list (with a warning);</li>
     *   <li>non-positive delays are dropped — a pass "0 minutes after trigger" is meaningless;</li>
     *   <li>levels are clamped to 1..5;</li>
     *   <li>the result is sorted by delay ascending, so an out-of-order config can never make a later
     *       pass come due before an earlier one.</li>
     * </ul>
     */
    public static DegradeSchedule fromConfig(List<? extends Integer> delaysInMinutes,
                                             List<? extends Integer> levels) {
        int paired = Math.min(delaysInMinutes.size(), levels.size());
        if (delaysInMinutes.size() != levels.size()) {
            LOGGER.warn("World Degrade: schedule passDelays ({}) and passLevels ({}) differ in length; "
                    + "using the first {} pair(s)", delaysInMinutes.size(), levels.size(), paired);
        }
        List<Pass> passes = new ArrayList<>(paired);
        for (int i = 0; i < paired; i++) {
            int minutes = delaysInMinutes.get(i);
            if (minutes <= 0) {
                LOGGER.warn("World Degrade: dropping schedule pass {} with non-positive delay {}", i, minutes);
                continue;
            }
            int level = Math.max(1, Math.min(5, levels.get(i)));
            passes.add(new Pass((long) minutes * MINUTE_TICKS, level));
        }
        passes.sort(Comparator.comparingLong(Pass::delayTicks));
        return new DegradeSchedule(List.copyOf(passes));
    }
}
