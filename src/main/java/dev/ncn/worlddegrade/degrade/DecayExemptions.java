package dev.ncn.worlddegrade.degrade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

public final class DecayExemptions {

    private DecayExemptions() {
    }

    public static boolean isExempt(BlockState state) {
        boolean builtin = isOre(state) || isUntouchedNature(state) || isIndestructible(state)
                || isCreativeSupply(state);
        return dev.ncn.worlddegrade.data.BlockCategories.is(
                state, dev.ncn.worlddegrade.data.BlockCategories.Category.EXEMPT, builtin);
    }

    public static boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) || isRawStorageBlock(state)
                || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES)
                || state.is(Blocks.NETHER_QUARTZ_ORE) || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.RAW_IRON_BLOCK) || state.is(Blocks.RAW_COPPER_BLOCK)
                || state.is(Blocks.RAW_GOLD_BLOCK);
    }

    private static boolean isRawStorageBlock(BlockState state) {
        for (TagKey<Block> tag : state.getTags().toList()) {
            if (tag.location().getNamespace().equals("c")
                    && tag.location().getPath().startsWith("storage_blocks/raw_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCreativeSupply(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("create")
                && (id.getPath().equals("creative_motor") || id.getPath().equals("creative_crate")
                        || id.getPath().equals("creative_fluid_tank"));
    }

    private static boolean isUntouchedNature(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUD)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.COBWEB) || state.is(Blocks.VINE)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.LEAVES) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MOSS_CARPET) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(Blocks.DIRT) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.CRIMSON_NYLIUM) || state.is(Blocks.WARPED_NYLIUM)
                || state.is(BlockTags.CAVE_VINES) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.LILY_PAD) || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.NETHER_WART) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.PUMPKIN_STEM) || state.is(Blocks.MELON_STEM)
                || state.is(Blocks.ATTACHED_PUMPKIN_STEM) || state.is(Blocks.ATTACHED_MELON_STEM)
                || state.is(Blocks.COCOA) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.CRIMSON_FUNGUS)
                || state.is(Blocks.WARPED_FUNGUS) || state.is(Blocks.WEEPING_VINES)
                || state.is(Blocks.WEEPING_VINES_PLANT) || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.TWISTING_VINES_PLANT) || state.is(Blocks.CHORUS_PLANT)
                || state.is(Blocks.CHORUS_FLOWER) || state.is(Blocks.SCULK)
                || state.is(Blocks.SCULK_VEIN) || state.is(BlockTags.CORALS)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.BIG_DRIPLEAF_STEM) || state.is(Blocks.SMALL_DRIPLEAF);
    }

    private static boolean isIndestructible(BlockState state) {
        return state.getBlock().defaultDestroyTime() < 0
                || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.REINFORCED_DEEPSLATE)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.STRUCTURE_VOID);
    }
}
