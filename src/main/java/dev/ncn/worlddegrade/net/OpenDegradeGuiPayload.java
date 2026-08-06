package dev.ncn.worlddegrade.net;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenDegradeGuiPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenDegradeGuiPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "open_gui"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, OpenDegradeGuiPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenDegradeGuiPayload());

    @Override
    public CustomPacketPayload.Type<OpenDegradeGuiPayload> type() {
        return TYPE;
    }
}
