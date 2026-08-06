package dev.ncn.worlddegrade.net;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.client.ClientPayloadHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class ModNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenDegradeGuiPayload.TYPE, OpenDegradeGuiPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleOpenGui(payload, context));
        registrar.playToServer(DegradeRequestPayload.TYPE, DegradeRequestPayload.STREAM_CODEC,
                ServerPayloadHandler::handleDegradeRequest);
        registrar.playToClient(MarkingPayloads.RegionsSync.TYPE, MarkingPayloads.RegionsSync.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleRegionsSync(payload, context));
        registrar.playToClient(MarkingPayloads.SelectionSync.TYPE, MarkingPayloads.SelectionSync.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleSelectionSync(payload, context));
        registrar.playToClient(MarkingPayloads.OpenMarkConfirm.TYPE, MarkingPayloads.OpenMarkConfirm.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleOpenMarkConfirm(payload, context));
        registrar.playToServer(MarkingPayloads.ConfirmMark.TYPE, MarkingPayloads.ConfirmMark.STREAM_CODEC,
                ServerPayloadHandler::handleConfirmMark);
        registrar.playToServer(MarkingPayloads.WandAirAttack.TYPE, MarkingPayloads.WandAirAttack.STREAM_CODEC,
                ServerPayloadHandler::handleWandAirAttack);
    }

    private ModNetworking() {
    }
}
