package dev.ncn.worlddegrade.degrade;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect;
import dev.ncn.worlddegrade.degrade.effects.VanillaDecayEffect;
import dev.ncn.worlddegrade.degrade.effects.WoodRotEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = WorldDegrade.MOD_ID)
public final class CoverageAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAMPLE = 40;

    private CoverageAudit() {
    }

    private static final String[] CREATE_FAMILY = {"create", "aeronautics", "simulated", "offroad"};

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        report("vanilla", "minecraft");
        if (net.neoforged.fml.ModList.get().isLoaded("chipped")) {
            report("Chipped", "chipped");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("rechiseled")) {
            report("Rechiseled", "rechiseled");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("rechiseledcreate")) {
            report("Rechiseled: Create", "rechiseledcreate");
        }
        for (String namespace : CREATE_FAMILY) {
            if (net.neoforged.fml.ModList.get().isLoaded(namespace)) {
                report(namespace, namespace);
            }
        }
    }

    private static void report(String label, String namespace) {
        int matched = 0;
        List<String> unmatched = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(namespace)) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (isCovered(state)) {
                matched++;
            } else {
                unmatched.add(id.getPath());
            }
        }
        int total = matched + unmatched.size();
        if (unmatched.isEmpty()) {
            LOGGER.info("World Degrade: {} coverage {}/{} blocks", label, matched, total);
            return;
        }
        LOGGER.info("World Degrade: {} coverage {}/{} blocks; {} fall to the generic break pass{}",
                label, matched, total, unmatched.size(),
                unmatched.size() > SAMPLE ? " (first " + SAMPLE + " shown)" : "");
        LOGGER.info("World Degrade: uncovered -> {}",
                String.join(", ", unmatched.subList(0, Math.min(SAMPLE, unmatched.size()))));
    }

    private static boolean isCovered(BlockState state) {
        Block block = state.getBlock();
        if (block.defaultDestroyTime() < 0) {
            return true;
        }
        return BrickWeatherEffect.isKnownMaterial(state)
                || DecayExemptions.isExempt(state)
                || VanillaDecayEffect.handles(state)
                || WoodRotEffect.isWood(state)
                || state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)
                || block instanceof DoorBlock || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock || block instanceof CampfireBlock
                || block instanceof TorchBlock || block instanceof LanternBlock
                || state.is(BlockTags.SHULKER_BOXES) || state.hasBlockEntity()
                || (net.neoforged.fml.ModList.get().isLoaded("chipped")
                        && dev.ncn.worlddegrade.compat.chipped.ChippedWeatherEffect.handles(state))
                || (net.neoforged.fml.ModList.get().isLoaded("create")
                        && dev.ncn.worlddegrade.compat.create.CreateDecayEffect.handles(state))
                || (net.neoforged.fml.ModList.get().isLoaded("rechiseled")
                        && dev.ncn.worlddegrade.compat.rechiseled.RechiseledMaskedMaterialEffect.handles(state));
    }
}
