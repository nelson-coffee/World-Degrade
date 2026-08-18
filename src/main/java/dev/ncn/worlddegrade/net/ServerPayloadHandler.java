package dev.ncn.worlddegrade.net;

import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.DegradeResult;
import dev.ncn.worlddegrade.degrade.DegradeService;
import dev.ncn.worlddegrade.item.ModItems;
import dev.ncn.worlddegrade.marking.MarkingService;
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
        DegradeLevel level = DegradeLevel.byId(payload.level());
        DegradeChances chances = payload.customChances() != null
                && payload.customChances().length == DegradeChances.VALUE_COUNT
                ? DegradeChances.custom(level.id(), payload.corruptComputers(), payload.customChances())
                : DegradeChances.of(level, payload.corruptComputers());
        int radius = Mth.clamp(payload.radius(), MIN_RADIUS, MAX_RADIUS);
        DegradeArea area = payload.wholeWorld()
                ? new DegradeArea.WholeDimension()
                : new DegradeArea.Radius(player.getX(), player.getZ(), radius);
        DegradeResult result = DegradeService.start(
                player.serverLevel(), area, chances, true, player.getUUID());
        if (!result.started()) {
            player.sendSystemMessage(Component.translatable(result.messageKey()));
        }
    }

    public static void handleConfirmMark(MarkingPayloads.ConfirmMark payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MarkingService.confirm(player);
        }
    }

    public static void handleWandAirAttack(MarkingPayloads.WandAirAttack payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.isShiftKeyDown()
                && (player.getMainHandItem().is(ModItems.MARKER_WAND.get())
                        || player.getOffhandItem().is(ModItems.MARKER_WAND.get()))) {
            MarkingService.deleteAimedRegion(player);
        }
    }

    private ServerPayloadHandler() {
    }
}
