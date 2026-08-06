package dev.ncn.worlddegrade.tracking;

import com.mojang.serialization.Codec;
import dev.ncn.worlddegrade.WorldDegrade;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;
import java.util.stream.LongStream;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WorldDegrade.MOD_ID);

    private static final Codec<LongOpenHashSet> LONG_SET_CODEC = Codec.LONG_STREAM
            .xmap(stream -> new LongOpenHashSet(stream.toArray()), set -> LongStream.of(set.toLongArray()));

    public static final Supplier<AttachmentType<LongOpenHashSet>> TRACKED_BLOCKS =
            ATTACHMENT_TYPES.register("tracked_blocks",
                    () -> AttachmentType.builder(() -> new LongOpenHashSet()).serialize(LONG_SET_CODEC).build());

    public static final Supplier<AttachmentType<LongOpenHashSet>> EXCAVATED_CEILINGS =
            ATTACHMENT_TYPES.register("excavated_ceilings",
                    () -> AttachmentType.builder(() -> new LongOpenHashSet()).serialize(LONG_SET_CODEC).build());

    private ModAttachments() {
    }
}
