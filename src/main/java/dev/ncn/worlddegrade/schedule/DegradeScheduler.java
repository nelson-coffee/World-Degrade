package dev.ncn.worlddegrade.schedule;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeJob;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.DegradeResult;
import dev.ncn.worlddegrade.degrade.DegradeService;
import dev.ncn.worlddegrade.undo.UndoManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Fires degradation schedules whose next pass has come due. Runs on the server tick, but only checks
 * every {@link #CHECK_INTERVAL_TICKS} ticks — one second is fine precision for minute-scale delays,
 * and the check is a cheap linear scan of at most a few hundred entries per dimension.
 *
 * <p>Timing uses {@link ServerLevel#getGameTime()}: persisted in level data, monotonic, advancing one
 * per tick only while the server runs and unaffected by {@code /time set}. That is exactly the
 * "server runtime, not wall clock" clock the issue asks for. Time during which the feature was
 * switched off does not count either — see {@link ScheduledDegradations#absorbPause}.
 *
 * <p>At most one pass starts per check, because {@link DegradeJob} has a single global slot — a second
 * start would only get {@link DegradeResult.Status#BUSY}. When the scheduler has fallen behind (feature
 * toggled off for a while, a long manual run holding the slot, chunks never loaded) several passes can
 * be overdue at once; {@link DegradeSchedule#latestDuePass} collapses them to the single highest
 * overdue level so a backlog does not fire every level in a burst. Scheduled passes always run with no
 * undo and with computer corruption disabled, since they fire unattended with no chance to revert.
 */
@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class DegradeScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final int CHECK_INTERVAL_TICKS = 20;

    private static int tickCounter;

    private DegradeScheduler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (!WorldDegradeConfig.scheduleEnabled()) {
            return;
        }
        DegradeSchedule schedule = WorldDegradeConfig.schedule();
        if (schedule.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        // Every dimension has its pause absorbed before anything fires. Doing it in the same loop as
        // the firing would mean the one-pass-per-check return skipped the dimensions after it, and
        // their next check would then see a double-length gap and absorb it as a phantom pause.
        for (ServerLevel level : server.getAllLevels()) {
            ScheduledDegradations store = ScheduledDegradations.existing(level);
            if (store == null) {
                continue;
            }
            long paused = store.absorbPause(level.getGameTime(), CHECK_INTERVAL_TICKS);
            if (paused > 0) {
                LOGGER.info("World Degrade: schedules in {} were paused for {} tick(s) (~{} minute(s)); "
                                + "their remaining delays were preserved",
                        level.dimension().location(), paused, paused / DegradeSchedule.MINUTE_TICKS);
            }
        }
        if (DegradeJob.isBusy() || UndoManager.isRestoring()) {
            // A run or an undo holding the single job slot blocks firing, but it is still elapsed
            // server runtime, so the clocks above kept advancing through it — only the feature being
            // switched off counts as paused.
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            ScheduledDegradations store = ScheduledDegradations.existing(level);
            if (store == null || store.entries().isEmpty()) {
                continue;
            }
            ScheduledDegradations.Entry due = store.pollDue(level.getGameTime(), schedule);
            if (due != null && fire(level, store, due, schedule)) {
                return;
            }
        }
    }

    /** @return true when this check has done its work (started a pass or pruned an entry). */
    private static boolean fire(ServerLevel level, ScheduledDegradations store,
                                ScheduledDegradations.Entry due, DegradeSchedule schedule) {
        // Jump over any intermediate passes that are also already overdue so a backlog resolves to one
        // pass at the highest overdue level rather than firing every level a check apart.
        int firePass = schedule.latestDuePass(due.triggerGameTime(), due.nextPass(), level.getGameTime());
        DegradeSchedule.Pass pass = schedule.passes().get(firePass);
        // No undo, and no computer corruption: an unattended pass must not garble ComputerCraft data
        // with no snapshot to restore it.
        DegradeChances chances = DegradeChances.of(DegradeLevel.byId(pass.levelId()), false);
        DegradeArea area = new DegradeArea.Chunks(new LongOpenHashSet(due.chunks()));
        DegradeResult result = DegradeService.start(level, area, chances, false, null);
        switch (result.status()) {
            case STARTED -> {
                if (firePass != due.nextPass()) {
                    LOGGER.info("World Degrade: schedule #{} was behind; skipping to overdue pass {} (level {})",
                            due.id(), firePass + 1, pass.levelId());
                }
                LOGGER.info("World Degrade: schedule #{} fired pass {} (level {}) over {} chunk(s) in {}",
                        due.id(), firePass + 1, pass.levelId(), due.chunkCount(),
                        level.dimension().location());
                store.advanceTo(due, firePass, schedule);
                return true;
            }
            case BUSY -> {
                return false;
            }
            case DIMENSION_DISABLED -> {
                LOGGER.warn("World Degrade: cancelling schedule #{} — dimension {} is disabled",
                        due.id(), level.dimension().location());
                store.removeById(due.id());
                return true;
            }
            case EMPTY_AREA -> {
                store.removeById(due.id());
                return true;
            }
            case NOTHING_FOUND -> {
                // Not a cancellation: the area may hold nothing tracked simply because
                // enablePlacementTracking is off, or its chunks are not loaded yet. Advance past this
                // pass like a completed one instead of destroying the whole multi-pass schedule.
                LOGGER.warn("World Degrade: schedule #{} pass {} found nothing tracked to degrade "
                        + "(is enablePlacementTracking on?); advancing past it",
                        due.id(), firePass + 1);
                store.advanceTo(due, firePass, schedule);
                return true;
            }
        }
        return false;
    }
}
