package dev.ncn.worlddegrade.tag;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The bounded set of {@code #worlddegrade:} block tags that decide which degradation effect a block
 * is eligible for. Modders tag their blocks with these for automatic categorization without a
 * datapack; the effects treat a tag hit as equivalent to the built-in hardcoded list.
 */
public final class ModBlockTags {

    public static final TagKey<Block> WOOD_ROT = create("wood_rot");
    public static final TagKey<Block> GLASS_BREAK = create("glass_break");
    public static final TagKey<Block> DOOR_BREAK = create("door_break");
    public static final TagKey<Block> MASONRY_DECAY = create("masonry_decay");
    public static final TagKey<Block> LIGHT_SNUFF = create("light_snuff");
    public static final TagKey<Block> EXEMPT = create("exempt");

    private static TagKey<Block> create(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, path));
    }

    private ModBlockTags() {
    }
}
