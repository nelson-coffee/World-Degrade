package dev.ncn.worlddegrade.data;

import dev.ncn.worlddegrade.tag.ModBlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides which degradation effect a block is eligible for, layering three sources on top of each
 * other: datapack {@code block_category} overrides (highest priority), the {@code #worlddegrade:}
 * block tags, and finally the effect's own built-in hardcoded check (the fallback that keeps vanilla
 * behaviour identical when nothing else applies).
 */
public final class BlockCategories {

    public enum Category {
        WOOD(ModBlockTags.WOOD_ROT),
        GLASS(ModBlockTags.GLASS_BREAK),
        DOOR(ModBlockTags.DOOR_BREAK),
        MASONRY(ModBlockTags.MASONRY_DECAY),
        LIGHT(ModBlockTags.LIGHT_SNUFF),
        EXEMPT(ModBlockTags.EXEMPT);

        private final TagKey<Block> tag;

        Category(TagKey<Block> tag) {
            this.tag = tag;
        }

        public TagKey<Block> tag() {
            return tag;
        }

        public static Category byName(String name) {
            for (Category category : values()) {
                if (category.name().equalsIgnoreCase(name)) {
                    return category;
                }
            }
            return null;
        }
    }

    private static final Map<Category, Set<Block>> ADDED = new EnumMap<>(Category.class);
    private static final Map<Category, Set<Block>> REMOVED = new EnumMap<>(Category.class);

    static {
        for (Category category : Category.values()) {
            ADDED.put(category, new HashSet<>());
            REMOVED.put(category, new HashSet<>());
        }
    }

    private BlockCategories() {
    }

    /** Wipes datapack category overrides ahead of a reload. */
    public static void clearOverrides() {
        for (Category category : Category.values()) {
            ADDED.get(category).clear();
            REMOVED.get(category).clear();
        }
    }

    public static void add(Category category, Block block) {
        ADDED.get(category).add(block);
        REMOVED.get(category).remove(block);
    }

    public static void remove(Category category, Block block) {
        REMOVED.get(category).add(block);
        ADDED.get(category).remove(block);
    }

    /**
     * @param builtin the effect's own hardcoded membership check, used only when no override or tag
     *                decides the block. Passing it in keeps each effect's bespoke logic where it lives
     *                instead of duplicating it here.
     */
    public static boolean is(BlockState state, Category category, boolean builtin) {
        Block block = state.getBlock();
        if (REMOVED.get(category).contains(block)) {
            return false;
        }
        if (ADDED.get(category).contains(block)) {
            return true;
        }
        if (state.is(category.tag())) {
            return true;
        }
        return builtin;
    }
}
