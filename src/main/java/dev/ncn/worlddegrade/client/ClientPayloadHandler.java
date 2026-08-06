package dev.ncn.worlddegrade.client;

import dev.ncn.worlddegrade.net.OpenDegradeGuiPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {

    public static void handleOpenGui(OpenDegradeGuiPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new DegradeScreen());
    }

    public static void handleRegionsSync(dev.ncn.worlddegrade.net.MarkingPayloads.RegionsSync payload,
                                         IPayloadContext context) {
        MarkedRegionsClientCache.setRegions(payload.regions());
    }

    public static void handleSelectionSync(dev.ncn.worlddegrade.net.MarkingPayloads.SelectionSync payload,
                                           IPayloadContext context) {
        MarkedRegionsClientCache.setSelection(payload.first(), payload.second());
    }

    public static void handleOpenMarkConfirm(dev.ncn.worlddegrade.net.MarkingPayloads.OpenMarkConfirm payload,
                                             IPayloadContext context) {
        Minecraft.getInstance().setScreen(new MarkConfirmScreen(payload.min(), payload.max(), payload.blockCount()));
    }

    private ClientPayloadHandler() {
    }
}
