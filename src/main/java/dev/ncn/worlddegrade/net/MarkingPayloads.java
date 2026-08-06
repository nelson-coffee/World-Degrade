package dev.ncn.worlddegrade.net;

import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.marking.MarkedRegions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MarkingPayloads {

    public record RegionsSync(List<MarkedRegions.Region> regions) implements CustomPacketPayload {
        public static final Type<RegionsSync> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "regions_sync"));
        public static final StreamCodec<ByteBuf, RegionsSync> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeInt(payload.regions().size());
                    for (MarkedRegions.Region region : payload.regions()) {
                        buf.writeLong(region.id().getMostSignificantBits());
                        buf.writeLong(region.id().getLeastSignificantBits());
                        buf.writeLong(region.min().asLong());
                        buf.writeLong(region.max().asLong());
                    }
                },
                buf -> {
                    int count = buf.readInt();
                    List<MarkedRegions.Region> regions = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        UUID id = new UUID(buf.readLong(), buf.readLong());
                        regions.add(new MarkedRegions.Region(id,
                                BlockPos.of(buf.readLong()), BlockPos.of(buf.readLong())));
                    }
                    return new RegionsSync(regions);
                });

        @Override
        public Type<RegionsSync> type() {
            return TYPE;
        }
    }

    public record SelectionSync(@Nullable BlockPos first, @Nullable BlockPos second) implements CustomPacketPayload {
        public static final Type<SelectionSync> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "selection_sync"));
        public static final StreamCodec<ByteBuf, SelectionSync> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBoolean(payload.first() != null);
                    if (payload.first() != null) {
                        buf.writeLong(payload.first().asLong());
                    }
                    buf.writeBoolean(payload.second() != null);
                    if (payload.second() != null) {
                        buf.writeLong(payload.second().asLong());
                    }
                },
                buf -> new SelectionSync(
                        buf.readBoolean() ? BlockPos.of(buf.readLong()) : null,
                        buf.readBoolean() ? BlockPos.of(buf.readLong()) : null));

        @Override
        public Type<SelectionSync> type() {
            return TYPE;
        }
    }

    public record OpenMarkConfirm(BlockPos min, BlockPos max, int blockCount) implements CustomPacketPayload {
        public static final Type<OpenMarkConfirm> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "open_mark_confirm"));
        public static final StreamCodec<ByteBuf, OpenMarkConfirm> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeLong(payload.min().asLong());
                    buf.writeLong(payload.max().asLong());
                    buf.writeInt(payload.blockCount());
                },
                buf -> new OpenMarkConfirm(BlockPos.of(buf.readLong()), BlockPos.of(buf.readLong()), buf.readInt()));

        @Override
        public Type<OpenMarkConfirm> type() {
            return TYPE;
        }
    }

    public record ConfirmMark() implements CustomPacketPayload {
        public static final Type<ConfirmMark> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "confirm_mark"));
        public static final StreamCodec<ByteBuf, ConfirmMark> STREAM_CODEC = StreamCodec.unit(new ConfirmMark());

        @Override
        public Type<ConfirmMark> type() {
            return TYPE;
        }
    }

    public record WandAirAttack() implements CustomPacketPayload {
        public static final Type<WandAirAttack> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "wand_air_attack"));
        public static final StreamCodec<ByteBuf, WandAirAttack> STREAM_CODEC = StreamCodec.unit(new WandAirAttack());

        @Override
        public Type<WandAirAttack> type() {
            return TYPE;
        }
    }

    private MarkingPayloads() {
    }
}
