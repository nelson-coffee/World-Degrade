package dev.ncn.worlddegrade.net;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record DegradeRequestPayload(int level, boolean wholeWorld, int radius,
                                    boolean corruptComputers,
                                    @Nullable float[] customChances) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DegradeRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "degrade_request"));

    public static final StreamCodec<ByteBuf, DegradeRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.level());
                buf.writeBoolean(payload.wholeWorld());
                buf.writeInt(payload.radius());
                buf.writeBoolean(payload.corruptComputers());
                buf.writeBoolean(payload.customChances() != null);
                if (payload.customChances() != null) {
                    for (int i = 0; i < DegradeChances.VALUE_COUNT; i++) {
                        buf.writeFloat(payload.customChances()[i]);
                    }
                }
            },
            buf -> {
                int level = buf.readByte();
                boolean wholeWorld = buf.readBoolean();
                int radius = buf.readInt();
                boolean corruptComputers = buf.readBoolean();
                float[] custom = null;
                if (buf.readBoolean()) {
                    custom = new float[DegradeChances.VALUE_COUNT];
                    for (int i = 0; i < DegradeChances.VALUE_COUNT; i++) {
                        custom[i] = buf.readFloat();
                    }
                }
                return new DegradeRequestPayload(level, wholeWorld, radius, corruptComputers, custom);
            });

    @Override
    public CustomPacketPayload.Type<DegradeRequestPayload> type() {
        return TYPE;
    }
}
