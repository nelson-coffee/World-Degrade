package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.data.BlockCategories;
import dev.ncn.worlddegrade.degrade.DecayExemptions;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.StructureShape;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class VanillaDecayEffect implements DegradeEffect {
    private static final float BREAK_SCALE = 0.33f;
    private final boolean enabled;

    public VanillaDecayEffect(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void apply(DegradeContext ctx) {
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.isAir()) {
                continue;
            }
            Family family = classify(state);
            if (family == null) {
                continue;
            }
            ctx.claim(pos);
            if (!enabled) {
                continue;
            }
            if (DecayExemptions.isExempt(state)) {
                continue;
            }
            if (!BrickWeatherEffect.isFullyWorn(ctx, pos, state)) {
                continue;
            }
            if (ctx.deferStructureToCollapse()
                    && StructureShape.classify(ctx, pos) != StructureShape.Part.NONE) {
                continue;
            }
            switch (family) {
                case CANDLE -> decayCandle(ctx, pos, state);
                case ICE -> meltIce(ctx, pos, state);
                default -> {
                    if (ctx.roll(ctx.patchChance(pos, rateFor(ctx, family)))) {
                        breakBlock(ctx, pos);
                    }
                }
            }
        }
    }

    private enum Family { FURNISHING, MECHANISM, MASONRY, CANDLE, ICE }

    public static boolean handles(BlockState state) {
        return classify(state) != null;
    }

    private float rateFor(DegradeContext ctx, Family family) {
        return switch (family) {
            case FURNISHING -> ctx.chances.woodRotChance();
            case MECHANISM -> ctx.chances.machineBreakChance();
            case MASONRY, ICE -> ctx.chances.brickWeatherChance() * BREAK_SCALE;
            case CANDLE -> ctx.chances.campfireExtinguishChance();
        };
    }

    private static Family classify(BlockState state) {
        Block block = state.getBlock();
        if (BrickWeatherEffect.isKnownMaterial(state) || WoodRotEffect.isWood(state)) {
            return null;
        }
        if (block instanceof CandleBlock || block instanceof CandleCakeBlock) {
            return Family.CANDLE;
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW)) {
            return Family.ICE;
        }
        if (isFurnishing(state)) {
            return Family.FURNISHING;
        }
        if (isMechanism(state)) {
            return Family.MECHANISM;
        }
        if (BlockCategories.is(state, BlockCategories.Category.MASONRY, isMasonry(state))) {
            return Family.MASONRY;
        }
        return null;
    }

    private static boolean isFurnishing(BlockState state) {
        return state.is(BlockTags.BEDS) || state.is(BlockTags.BANNERS)
                || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.FLOWER_POTS)
                || state.is(Blocks.SKELETON_SKULL) || state.is(Blocks.SKELETON_WALL_SKULL)
                || state.is(Blocks.WITHER_SKELETON_SKULL) || state.is(Blocks.WITHER_SKELETON_WALL_SKULL)
                || state.is(Blocks.ZOMBIE_HEAD) || state.is(Blocks.ZOMBIE_WALL_HEAD)
                || state.is(Blocks.PLAYER_HEAD) || state.is(Blocks.PLAYER_WALL_HEAD)
                || state.is(Blocks.CREEPER_HEAD) || state.is(Blocks.CREEPER_WALL_HEAD)
                || state.is(Blocks.DRAGON_HEAD) || state.is(Blocks.DRAGON_WALL_HEAD)
                || state.is(Blocks.PIGLIN_HEAD) || state.is(Blocks.PIGLIN_WALL_HEAD)
                || state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF)
                || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.CARTOGRAPHY_TABLE)
                || state.is(Blocks.FLETCHING_TABLE) || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.LOOM) || state.is(Blocks.LADDER) || state.is(Blocks.SCAFFOLDING)
                || state.is(Blocks.CAKE) || state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE)
                || state.is(Blocks.SEA_PICKLE) || state.is(Blocks.TURTLE_EGG)
                || state.is(Blocks.SNIFFER_EGG) || state.is(Blocks.FROGSPAWN)
                || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.DRIED_KELP_BLOCK)
                || state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.MELON) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.HONEYCOMB_BLOCK) || state.is(Blocks.COMPOSTER);
    }

    private static boolean isMechanism(BlockState state) {
        return state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(BlockTags.RAILS) || state.is(Blocks.LEVER)
                || state.is(Blocks.REPEATER) || state.is(Blocks.COMPARATOR)
                || state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(Blocks.PISTON)
                || state.is(Blocks.STICKY_PISTON) || state.is(Blocks.PISTON_HEAD)
                || state.is(Blocks.MOVING_PISTON) || state.is(Blocks.OBSERVER)
                || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)
                || state.is(Blocks.NOTE_BLOCK) || state.is(Blocks.TARGET)
                || state.is(Blocks.DAYLIGHT_DETECTOR) || state.is(Blocks.TRIPWIRE)
                || state.is(Blocks.TRIPWIRE_HOOK) || state.is(Blocks.LIGHTNING_ROD)
                || state.is(Blocks.CHAIN) || state.is(Blocks.IRON_BARS)
                || state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.TNT);
    }

    private static boolean isMasonry(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.BRICKS)
                || state.is(Blocks.BRICK_STAIRS) || state.is(Blocks.BRICK_SLAB)
                || state.is(Blocks.BRICK_WALL) || state.is(Blocks.IRON_BLOCK)
                || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.DIAMOND_BLOCK)
                || state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)
                || state.is(Blocks.LAPIS_BLOCK) || state.is(Blocks.REDSTONE_BLOCK)
                || state.is(Blocks.COAL_BLOCK) || state.is(Blocks.AMETHYST_BLOCK)
                || state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.SLIME_BLOCK)
                || state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.BONE_BLOCK)
                || state.is(Blocks.GLOWSTONE) || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.SHROOMLIGHT) || state.is(Blocks.OCHRE_FROGLIGHT)
                || state.is(Blocks.VERDANT_FROGLIGHT) || state.is(Blocks.PEARLESCENT_FROGLIGHT)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CALCITE)
                || state.is(Blocks.GRINDSTONE) || state.is(Blocks.STONECUTTER)
                || state.is(Blocks.BELL) || state.is(Blocks.LODESTONE)
                || state.is(Blocks.CAULDRON) || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.BEACON) || state.is(Blocks.CONDUIT)
                || state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.SPAWNER)
                || state.is(Blocks.TRIAL_SPAWNER) || state.is(Blocks.VAULT)
                || state.is(Blocks.DRAGON_EGG) || state.is(Blocks.HEAVY_CORE)
                || state.is(Blocks.WARPED_WART_BLOCK)
                || state.is(Blocks.BASALT) || state.is(Blocks.POLISHED_BASALT)
                || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.CRIMSON_NYLIUM)
                || state.is(Blocks.WARPED_NYLIUM)
                || block instanceof net.minecraft.world.level.block.InfestedBlock
                || state.is(BlockTags.CAULDRONS) || state.is(BlockTags.CANDLE_CAKES)
                || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES)
                || state.is(Blocks.RED_NETHER_BRICKS) || state.is(Blocks.END_ROD)
                || state.is(BlockTags.CORAL_BLOCKS)
                || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
                        .getPath().contains("copper");
    }

    private void decayCandle(DegradeContext ctx, BlockPos pos, BlockState state) {
        if (!ctx.roll(ctx.chances.campfireExtinguishChance())) {
            return;
        }
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            ctx.replaceBlock(pos, state.setValue(BlockStateProperties.LIT, false));
            return;
        }
        if (state.getBlock() instanceof CandleCakeBlock) {
            ctx.replaceBlock(pos, Blocks.CAKE.defaultBlockState());
            return;
        }
        int candles = state.hasProperty(CandleBlock.CANDLES) ? state.getValue(CandleBlock.CANDLES) : 1;
        if (candles > 1) {
            ctx.replaceBlock(pos, state.setValue(CandleBlock.CANDLES, candles - 1));
        } else {
            ctx.removeBlock(pos);
        }
    }

    private void meltIce(DegradeContext ctx, BlockPos pos, BlockState state) {
        if (ctx.level.getBiome(pos).value().getBaseTemperature() <= 0.15f) {
            return;
        }
        if (!ctx.roll(ctx.patchChance(pos, ctx.chances.brickWeatherChance() * BREAK_SCALE))) {
            return;
        }
        Block thawed = null;
        if (state.is(Blocks.BLUE_ICE)) {
            thawed = Blocks.PACKED_ICE;
        } else if (state.is(Blocks.PACKED_ICE)) {
            thawed = Blocks.ICE;
        } else if (state.is(Blocks.SNOW_BLOCK)) {
            thawed = Blocks.SNOW;
        }
        if (thawed != null) {
            ctx.replaceBlock(pos, thawed.defaultBlockState());
        } else {
            ctx.removeBlock(pos);
        }
    }

    private void breakBlock(DegradeContext ctx, BlockPos pos) {
        if (ctx.blockEntity(pos) != null) {
            ctx.removeBlockAndWipeContents(pos);
        } else {
            ctx.removeBlock(pos);
        }
    }
}
