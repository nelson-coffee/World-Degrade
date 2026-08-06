package dev.ncn.worlddegrade.degrade;

import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class VanillaWearTable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] COLOURS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private static final float OBSIDIAN_RATE = 0.14f;

    private VanillaWearTable() {
    }

    public static void register() {
        int before = 0;

        link("anvil", "chipped_anvil");
        link("chipped_anvil", "damaged_anvil");

        link("amethyst_cluster", "large_amethyst_bud");
        link("large_amethyst_bud", "medium_amethyst_bud");
        link("medium_amethyst_bud", "small_amethyst_bud");

        for (String colour : COLOURS) {
            link(colour + "_concrete", colour + "_concrete_powder");
            link(colour + "_glazed_terracotta", "terracotta");
        }
        link("jack_o_lantern", "carved_pumpkin");

        for (String coral : new String[]{"tube", "brain", "bubble", "fire", "horn"}) {
            link(coral + "_coral_block", "dead_" + coral + "_coral_block");
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("potted_")) {
                BrickWeatherEffect.addWear(block, Blocks.FLOWER_POT);
                before++;
            }
        }

        link("farmland", "dirt");
        link("dirt_path", "dirt");
        link("dirt", "grass_block");

        BrickWeatherEffect.addWear(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, OBSIDIAN_RATE);

        LOGGER.info("World Degrade: registered vanilla decay chains ({} potted plants)", before);
    }

    private static void link(String fromPath, String toPath) {
        Block from = byId(fromPath);
        Block to = byId(toPath);
        if (from == null || to == null) {
            LOGGER.warn("World Degrade: vanilla chain {} -> {} did not resolve", fromPath, toPath);
            return;
        }
        BrickWeatherEffect.addWear(from, to);
    }

    @Nullable
    private static Block byId(String path) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path));
        return block == Blocks.AIR ? null : block;
    }
}
