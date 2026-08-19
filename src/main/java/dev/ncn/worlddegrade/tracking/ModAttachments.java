package dev.ncn.worlddegrade.tracking;

import dev.ncn.worlddegrade.WorldDegrade;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WorldDegrade.MOD_ID);

    public static final Supplier<AttachmentType<SectionBitStore>> TRACKED_BLOCKS =
            ATTACHMENT_TYPES.register("tracked_blocks",
                    () -> AttachmentType.builder(SectionBitStore::new)
                            .serialize(SectionBitStore.CODEC, store -> !store.isEmpty())
                            .build());

    public static final Supplier<AttachmentType<SectionBitStore>> EXCAVATED_CEILINGS =
            ATTACHMENT_TYPES.register("excavated_ceilings",
                    () -> AttachmentType.builder(SectionBitStore::new)
                            .serialize(SectionBitStore.CODEC, store -> !store.isEmpty())
                            .build());

    private ModAttachments() {
    }
}
