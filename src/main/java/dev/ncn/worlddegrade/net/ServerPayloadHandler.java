package dev.ncn.worlddegrade.net;

import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeJob;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.undo.UndoManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandler {
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 2048;

    public static void handleDegradeRequest(DegradeRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("chat.worlddegrade.no_permission"));
            return;
        }
        if (DegradeJob.isBusy() || UndoManager.isRestoring()) {
            player.sendSystemMessage(Component.translatable("chat.worlddegrade.busy"));
            return;
        }
        DegradeLevel level = DegradeLevel.byId(payload.level());
        DegradeChances chances = payload.customChances() != null
                && payload.customChances().length == DegradeChances.VALUE_COUNT
                ? DegradeChances.custom(level.id(), payload.corruptComputers(), payload.customChances())
                : DegradeChances.of(level, payload.corruptComputers());
        int radius = Mth.clamp(payload.radius(), MIN_RADIUS, MAX_RADIUS);
        DegradeJob.start(player, chances, payload.wholeWorld(), radius);
    }

    public static void handleConfirmMark(MarkingPayloads.ConfirmMark payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.ncn.worlddegrade.marking.MarkingService.confirm(player);
        }
    }

    public static void handleWandAirAttack(MarkingPayloads.WandAirAttack payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.isShiftKeyDown()
                && (player.getMainHandItem().is(dev.ncn.worlddegrade.item.ModItems.MARKER_WAND.get())
                        || player.getOffhandItem().is(dev.ncn.worlddegrade.item.ModItems.MARKER_WAND.get()))) {
            dev.ncn.worlddegrade.marking.MarkingService.deleteAimedRegion(player);
        }
    }

    private ServerPayloadHandler() {
    }
}
