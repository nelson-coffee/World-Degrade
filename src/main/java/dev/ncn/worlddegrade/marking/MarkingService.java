package dev.ncn.worlddegrade.marking;

import dev.ncn.worlddegrade.net.MarkingPayloads;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class MarkingService {
    public static final int MAX_EDGE = 128;
    private static final double DELETE_RAY_DISTANCE = 64.0;

    public static void handleBlockClick(ServerPlayer player, BlockPos clicked) {
        WandSelections.Selection selection = WandSelections.get(player.getUUID());
        if (selection != null && selection.second() != null && selection.contains(clicked)) {
            offerConfirm(player, selection);
            return;
        }
        WandSelections.Selection updated = selection == null
                ? new WandSelections.Selection(clicked, null)
                : new WandSelections.Selection(selection.first(), clicked);
        WandSelections.set(player.getUUID(), updated);
        syncSelection(player, updated);
    }

    public static void handleAirClick(ServerPlayer player) {
        WandSelections.Selection selection = WandSelections.get(player.getUUID());
        if (selection != null && selection.second() != null) {
            offerConfirm(player, selection);
        }
    }

    public static void clearSelection(ServerPlayer player) {
        WandSelections.clear(player.getUUID());
        PacketDistributor.sendToPlayer(player, new MarkingPayloads.SelectionSync(null, null));
        player.displayClientMessage(Component.translatable("chat.worlddegrade.wand.cleared"), true);
    }

    private static void offerConfirm(ServerPlayer player, WandSelections.Selection selection) {
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        if (isOversized(min, max)) {
            player.displayClientMessage(Component.translatable("chat.worlddegrade.wand.toobig",
                    MAX_EDGE, sizeString(min, max)), false);
            return;
        }
        int nonAir = countNonAir(player.serverLevel(), min, max);
        PacketDistributor.sendToPlayer(player, new MarkingPayloads.OpenMarkConfirm(min, max, nonAir));
    }

    public static void confirm(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }
        WandSelections.Selection selection = WandSelections.get(player.getUUID());
        if (selection == null || selection.second() == null) {
            return;
        }
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        if (isOversized(min, max)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        MarkedRegions.get(level).add(new MarkedRegions.Region(UUID.randomUUID(), min, max));
        WandSelections.clear(player.getUUID());
        PacketDistributor.sendToPlayer(player, new MarkingPayloads.SelectionSync(null, null));
        broadcastRegions(level);
        player.displayClientMessage(Component.translatable("chat.worlddegrade.wand.marked",
                sizeString(min, max)), false);
    }

    public static void deleteAimedRegion(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        MarkedRegions regions = MarkedRegions.get(level);
        Vec3 eye = player.getEyePosition();
        MarkedRegions.Region hit = regions.rayPick(eye, player.getLookAngle(), DELETE_RAY_DISTANCE);
        if (hit == null) {
            return;
        }
        regions.remove(hit.id());
        unmarkRegion(level, hit);
        broadcastRegions(level);
        player.displayClientMessage(Component.translatable("chat.worlddegrade.wand.removed",
                sizeString(hit.min(), hit.max())), false);
    }

    private static void unmarkRegion(ServerLevel level, MarkedRegions.Region region) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = region.min().getX(); x <= region.max().getX(); x++) {
            for (int y = region.min().getY(); y <= region.max().getY(); y++) {
                for (int z = region.min().getZ(); z <= region.max().getZ(); z++) {
                    PlacementTracker.untrack(level, cursor.set(x, y, z));
                }
            }
        }
    }

    public static void broadcastRegions(ServerLevel level) {
        MarkingPayloads.RegionsSync payload = new MarkingPayloads.RegionsSync(MarkedRegions.get(level).all());
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void syncRegionsTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new MarkingPayloads.RegionsSync(MarkedRegions.get(player.serverLevel()).all()));
    }

    private static void syncSelection(ServerPlayer player, WandSelections.Selection selection) {
        PacketDistributor.sendToPlayer(player,
                new MarkingPayloads.SelectionSync(selection.first(), selection.second()));
    }

    private static boolean isOversized(BlockPos min, BlockPos max) {
        return max.getX() - min.getX() + 1 > MAX_EDGE
                || max.getY() - min.getY() + 1 > MAX_EDGE
                || max.getZ() - min.getZ() + 1 > MAX_EDGE;
    }

    private static int countNonAir(ServerLevel level, BlockPos min, BlockPos max) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).isAir()) {
                count++;
            }
        }
        return count;
    }

    private static String sizeString(BlockPos min, BlockPos max) {
        return (max.getX() - min.getX() + 1) + "x" + (max.getY() - min.getY() + 1)
                + "x" + (max.getZ() - min.getZ() + 1);
    }

    private MarkingService() {
    }
}
