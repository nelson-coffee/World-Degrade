package dev.ncn.worlddegrade.schedule;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Releases a whole schedule when a block is <em>placed</em> anywhere inside it: an area something is
 * building in again is in use, and the whole unit — not just the touched chunk — should stop
 * degrading. Breaking is deliberately not a trigger: looting or mining a ruin is what happens
 * <em>because</em> it is abandoned, and letting it protect the ruin would let a griefer preserve a
 * wreck by smashing one block.
 *
 * <p>Anything acting through a player counts, machines included: a real player, a fake player, a
 * Create deployer on a contraption. What matters is that something is actively building here, not who
 * holds the item. Placements with no entity behind them — dispensers, pistons, Create rollers — are
 * invisible here, as are mobs, which are not placers in that sense: an enderman moving a block is not
 * somebody moving back in.
 *
 * <p>The hot-path guard is the {@code scheduleEnabled()} check, which is off by default and skips the
 * handler entirely; when on, the work is one reverse-map probe — the same order of magnitude as the
 * tracking lookup already done on every placement.
 *
 * <p>Runs at {@link EventPriority#LOWEST} so a claim/protection mod that vetoes the placement at a
 * higher priority wins: the bus does not deliver cancelled events to a listener that has not asked
 * for them, so a block that never actually landed cannot spare the schedule.
 */
@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class ScheduleReleaseEvents {

    private ScheduleReleaseEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            return;
        }
        release(event.getEntity(), event.getLevel(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        // Count every placed block: a multi-place straddling two schedules should release both, and
        // with a threshold above 1 each block in the event should count toward it.
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            release(event.getEntity(), event.getLevel(), snapshot.getPos());
        }
    }

    private static void release(@Nullable Entity entity, LevelAccessor levelAccessor, BlockPos pos) {
        if (!WorldDegradeConfig.scheduleEnabled()) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }
        if (!(levelAccessor instanceof ServerLevel level)) {
            return;
        }
        ScheduleService.markInUse(level, pos);
    }
}
