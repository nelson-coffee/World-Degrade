package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.degrade.DegradeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BrickWeatherEffect implements DegradeEffect {
    private static final Map<Block, Block> WEAR = new HashMap<>();

    private static final Set<Block> KNOWN = new HashSet<>();

    private static final Map<Block, Float> RATE_SCALE = new HashMap<>();

    private static final Map<Block, Integer> STEP_BONUS = new HashMap<>();

    private static final int[] STEP_BUDGET = {1, 1, 2, 3, 4};

    static {
        wear(Blocks.CHISELED_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        wear(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        wear(Blocks.CRACKED_STONE_BRICKS, Blocks.COBBLESTONE);
        wear(Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE);
        wear(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS);
        wear(Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB);
        wear(Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL);
        wear(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.COBBLESTONE_STAIRS);
        wear(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.COBBLESTONE_SLAB);
        wear(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.COBBLESTONE_WALL);

        wear(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        wear(Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS);
        wear(Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB);
        wear(Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL);

        wear(Blocks.SMOOTH_STONE, Blocks.STONE);
        wear(Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_SLAB);
        wear(Blocks.STONE, Blocks.COBBLESTONE);
        wear(Blocks.STONE_STAIRS, Blocks.COBBLESTONE_STAIRS);
        wear(Blocks.STONE_SLAB, Blocks.COBBLESTONE_SLAB);

        wear(Blocks.POLISHED_ANDESITE, Blocks.ANDESITE);
        wear(Blocks.POLISHED_ANDESITE_STAIRS, Blocks.ANDESITE_STAIRS);
        wear(Blocks.POLISHED_ANDESITE_SLAB, Blocks.ANDESITE_SLAB);
        wear(Blocks.POLISHED_DIORITE, Blocks.DIORITE);
        wear(Blocks.POLISHED_DIORITE_STAIRS, Blocks.DIORITE_STAIRS);
        wear(Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_SLAB);
        wear(Blocks.POLISHED_GRANITE, Blocks.GRANITE);
        wear(Blocks.POLISHED_GRANITE_STAIRS, Blocks.GRANITE_STAIRS);
        wear(Blocks.POLISHED_GRANITE_SLAB, Blocks.GRANITE_SLAB);

        wear(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS);
        wear(Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_BRICK_SLAB);
        wear(Blocks.DEEPSLATE_TILE_WALL, Blocks.DEEPSLATE_BRICK_WALL);
        wear(Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS);
        wear(Blocks.DEEPSLATE_BRICK_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB);
        wear(Blocks.DEEPSLATE_BRICK_WALL, Blocks.COBBLED_DEEPSLATE_WALL);
        wear(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
        wear(Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS);
        wear(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS);
        wear(Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE);
        wear(Blocks.CHISELED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        wear(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        wear(Blocks.POLISHED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE);
        wear(Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS);
        wear(Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB);
        wear(Blocks.POLISHED_DEEPSLATE_WALL, Blocks.COBBLED_DEEPSLATE_WALL);

        wear(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS);
        wear(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        wear(Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_WALL);
        wear(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        wear(Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        wear(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE);
        wear(Blocks.GILDED_BLACKSTONE, Blocks.BLACKSTONE);
        wear(Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE);
        wear(Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        wear(Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        wear(Blocks.POLISHED_BLACKSTONE_WALL, Blocks.BLACKSTONE_WALL);

        wear(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
        wear(Blocks.CHISELED_NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS);

        wear(Blocks.CHISELED_TUFF_BRICKS, Blocks.TUFF_BRICKS);
        wear(Blocks.TUFF_BRICKS, Blocks.TUFF);
        wear(Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_STAIRS);
        wear(Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_SLAB);
        wear(Blocks.TUFF_BRICK_WALL, Blocks.TUFF_WALL);
        wear(Blocks.CHISELED_TUFF, Blocks.TUFF);
        wear(Blocks.POLISHED_TUFF, Blocks.TUFF);
        wear(Blocks.POLISHED_TUFF_STAIRS, Blocks.TUFF_STAIRS);
        wear(Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_SLAB);
        wear(Blocks.POLISHED_TUFF_WALL, Blocks.TUFF_WALL);

        wear(Blocks.CUT_SANDSTONE, Blocks.SANDSTONE);
        wear(Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_SLAB);
        wear(Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE);
        wear(Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE);
        wear(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SANDSTONE_STAIRS);
        wear(Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE_SLAB);
        wear(Blocks.CUT_RED_SANDSTONE, Blocks.RED_SANDSTONE);
        wear(Blocks.CUT_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB);
        wear(Blocks.CHISELED_RED_SANDSTONE, Blocks.RED_SANDSTONE);
        wear(Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE);
        wear(Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_STAIRS);
        wear(Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB);

        wear(Blocks.END_STONE_BRICKS, Blocks.END_STONE);
        wear(Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BLOCK);
        wear(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_BLOCK);
        wear(Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_BLOCK);
        wear(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK);
        wear(Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.QUARTZ_STAIRS);
        wear(Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_SLAB);
        wear(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE);
        wear(Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_STAIRS);
        wear(Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_SLAB);
        wear(Blocks.DARK_PRISMARINE, Blocks.PRISMARINE);
        wear(Blocks.DARK_PRISMARINE_STAIRS, Blocks.PRISMARINE_STAIRS);
        wear(Blocks.DARK_PRISMARINE_SLAB, Blocks.PRISMARINE_SLAB);
        wear(Blocks.PURPUR_PILLAR, Blocks.PURPUR_BLOCK);
        wear(Blocks.MUD_BRICKS, Blocks.PACKED_MUD);

    }

    private static void wear(Block from, Block to) {
        WEAR.put(from, to);
        KNOWN.add(from);
        KNOWN.add(to);
    }

    public static void addWear(Block from, Block to) {
        addWear(from, to, 1.0f);
    }

    public static void addWear(Block from, Block to, float rateScale) {
        addWear(from, to, rateScale, 0);
    }

    public static void addWear(Block from, Block to, float rateScale, int stepBonus) {
        if (from != null && to != null && from != to && !from.defaultBlockState().isAir()) {
            wear(from, to);
            if (rateScale != 1.0f) {
                RATE_SCALE.put(from, rateScale);
            }
            if (stepBonus > 0) {
                STEP_BONUS.put(from, stepBonus);
            }
        }
    }

    @Nullable
    public static Block wearTarget(Block from) {
        return WEAR.get(from);
    }

    public static boolean isKnownMaterial(BlockState state) {
        Block block = state.getBlock();
        return KNOWN.contains(block) || block instanceof WeatheringCopper
                || weather(state) != null;
    }

    public static boolean isFullyWorn(BlockState state) {
        if (dev.ncn.worlddegrade.degrade.DecayExemptions.isExempt(state)) {
            return false;
        }
        return dev.ncn.worlddegrade.compat.CompatManager.isFullyWorn(state);
    }

    public static boolean isFullyWorn(dev.ncn.worlddegrade.degrade.DegradeContext ctx,
                                      net.minecraft.core.BlockPos pos, BlockState state) {
        if (dev.ncn.worlddegrade.degrade.DecayExemptions.isExempt(state)) {
            return false;
        }
        return dev.ncn.worlddegrade.compat.CompatManager.isFullyWorn(ctx, pos, state);
    }

    @Override
    public void apply(DegradeContext ctx) {
        int budget = STEP_BUDGET[Mth.clamp(ctx.chances.levelId(), 1, 5) - 1];
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState original = ctx.state(pos);
            if (original.isAir()) {
                continue;
            }
            if (isKnownMaterial(original)) {
                ctx.claim(pos);
            }
            BlockState current = original;
            for (int step = 0, allowance = budget; step < allowance; step++) {
                BlockState next = weather(current);
                if (next == null) {
                    break;
                }
                allowance = Math.max(allowance,
                        step + 1 + STEP_BONUS.getOrDefault(current.getBlock(), 0));
                float rate = ctx.chances.brickWeatherChance()
                        * RATE_SCALE.getOrDefault(current.getBlock(), 1.0f);
                if (!ctx.roll(ctx.patchChance(pos, rate))) {
                    break;
                }
                current = next;
            }
            if (current != original) {
                ctx.replaceBlock(pos, current);
            }
        }
    }

    @Nullable
    public static BlockState weather(BlockState state) {
        Block block = state.getBlock();
        Block worn = WEAR.get(block);
        if (worn != null) {
            return worn.withPropertiesOf(state);
        }
        return WeatheringCopper.getNext(block)
                .map(next -> next.withPropertiesOf(state))
                .orElse(null);
    }
}
