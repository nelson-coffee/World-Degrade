package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class CreateDecayEffect implements DegradeEffect {
    private static final float GEAR_DEBRIS_CHANCE = 0.4f;
    private static final int FLOOR_SEARCH_DEPTH = 32;
    private static final float BREAK_SCALE = 0.33f;

    private static final Set<Block> SPENT_IRON = ids("weathered_iron_block", "weathered_iron_window");

    private static final Set<Block> METALWORK = ids(
            "metal_girder", "metal_bracket",
            "andesite_ladder", "brass_ladder", "copper_ladder",
            "andesite_scaffolding", "brass_scaffolding", "copper_scaffolding");

    private static final Set<Block> PERISHABLE = ids("wooden_bracket", "cardboard_block");

    private static final Set<Block> GLAZING = ids(
            "ornate_iron_window",
            "oak_window", "spruce_window", "birch_window", "jungle_window", "acacia_window",
            "dark_oak_window", "mangrove_window", "cherry_window", "bamboo_window",
            "crimson_window", "warped_window");

    private static final Set<Block> HARDWARE = ids(
            "linear_chassis", "secondary_linear_chassis", "radial_chassis",
            "rope", "pulley_magnet", "piston_extension_pole", "mechanical_piston_head",
            "gantry_shaft", "gantry_carriage", "sticker", "minecart_anchor");

    private static final Set<Block> METAL_SOLIDS = ids(
            "brass_block", "zinc_block", "andesite_alloy_block",
            "experience_block", "rose_quartz_lamp");

    private static final Set<Block> RAILS = ids("track", "fake_track", "large_bogey", "small_bogey");

    private static final float RAIL_SCALE = 0.24f;

    private static final Set<Block> LOOSE_MACHINERY = ids(
            "water_wheel_structure", "steam_whistle_extension", "mechanical_plough",
            "controls", "powered_latch", "powered_toggle_latch", "copycat_base");

    private static final Set<Block> ADDON_MACHINERY =
            idsIn("simulated", "paired_docking_connector");

    private static final Set<Block> DECORATIVE_FLAME = ids("lit_blaze_burner");

    private static final Set<String> CREATE_FAMILY =
            Set.of("create", "aeronautics", "simulated", "offroad");

    private static final String[] CLOTH_SUFFIXES = {
            "_seat", "_table_cloth", "_sail", "_nameplate"};
    private static final Set<Block> CLOTH_EXTRAS = ids("placard", "clipboard");

    private static final String[] METAL_FITTING_SUFFIXES = {"_handle", "_bars"};
    private static final Set<Block> METAL_FITTING_EXTRAS = ids("sail_frame");

    private static Block byId(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
    }

    private static boolean suffixed(Block block, String... suffixes) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (!CREATE_FAMILY.contains(id.getNamespace())) {
            return false;
        }
        for (String suffix : suffixes) {
            if (id.getPath().endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLevitite(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("aeronautics") && id.getPath().contains("levitite");
    }

    private static Set<Block> ids(String... paths) {
        return idsIn("create", paths);
    }

    private static Set<Block> idsIn(String namespace, String... paths) {
        Set<Block> blocks = new HashSet<>();
        for (String path : paths) {
            Block block = BuiltInRegistries.BLOCK
                    .get(ResourceLocation.fromNamespaceAndPath(namespace, path));
            if (block != Blocks.AIR) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    @Override
    public void apply(DegradeContext ctx) {
        float machineChance = ctx.chances.machineBreakChance();
        for (long packed : ctx.positions()) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = ctx.state(pos);
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            if (block instanceof BeltBlock) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlockAndWipeContents(pos);
                }
            } else if (block instanceof FluidPipeBlock || block instanceof EncasedPipeBlock
                    || block instanceof GlassFluidPipeBlock) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlock(pos);
                }
            } else if (block instanceof ICogWheel || block instanceof AbstractSimpleShaftBlock) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlock(pos);
                    if (ctx.roll(GEAR_DEBRIS_CHANCE)) {
                        ctx.scatterDebris(pos, state, FLOOR_SEARCH_DEPTH);
                    }
                }
            } else if (block instanceof CasingBlock) {
                ctx.claim(pos);
                float chance = isEnvelope(block)
                        ? ctx.chances.envelopeBreakChance()
                        : ctx.chances.woodRotChance();
                if (ctx.roll(ctx.patchChance(pos, chance))) {
                    ctx.removeBlock(pos);
                }
            } else if (block instanceof BlazeBurnerBlock) {
                ctx.claim(pos);
                decayBlazeBurner(ctx, pos, state, machineChance);
            } else if (SPENT_IRON.contains(block) || PERISHABLE.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(ctx.patchChance(pos, ctx.chances.woodRotChance()))) {
                    ctx.removeBlock(pos);
                }
            } else if (GLAZING.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(ctx.chances.glassBreakChance())) {
                    ctx.removeBlock(pos);
                }
            } else if (METALWORK.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlock(pos);
                }
            } else if (block instanceof CopycatBlock) {
                ctx.claim(pos);
                CopycatDecay.decay(ctx, pos, state, machineChance);
            } else if (RAILS.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(ctx.patchChance(pos, machineChance * RAIL_SCALE))) {
                    ctx.removeBlockAndWipeContents(pos);
                }
            } else if (suffixed(block, CLOTH_SUFFIXES) || CLOTH_EXTRAS.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(ctx.patchChance(pos, ctx.chances.woodRotChance()))) {
                    ctx.removeBlockAndWipeContents(pos);
                }
            } else if (suffixed(block, METAL_FITTING_SUFFIXES)
                    || METAL_FITTING_EXTRAS.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlock(pos);
                }
            } else if (METAL_SOLIDS.contains(block) || isLevitite(block)) {
                ctx.claim(pos);
                if (ctx.roll(ctx.patchChance(pos, ctx.chances.brickWeatherChance() * BREAK_SCALE))) {
                    ctx.removeBlock(pos);
                }
            } else if (HARDWARE.contains(block) || LOOSE_MACHINERY.contains(block)
                    || ADDON_MACHINERY.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlock(pos);
                }
            } else if (DECORATIVE_FLAME.contains(block)) {
                ctx.claim(pos);
                if (ctx.roll(ctx.chances.campfireExtinguishChance())) {
                    ctx.removeBlock(pos);
                }
            } else if (ctx.blockEntity(pos) instanceof SmartBlockEntity) {
                ctx.claim(pos);
                if (ctx.roll(machineChance)) {
                    ctx.removeBlockAndWipeContents(pos);
                }
            }
        }
    }

    private void decayBlazeBurner(DegradeContext ctx, BlockPos pos, BlockState state, float machineChance) {
        if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE) {
            if (ctx.roll(machineChance)) {
                ctx.removeBlock(pos);
            }
            return;
        }
        if (!ctx.roll(ctx.patchChance(pos, ctx.chances.brickWeatherChance()))) {
            return;
        }
        ctx.replaceBlockDiscardingEntity(pos,
                state.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.NONE));
    }

    public static boolean handles(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BeltBlock || block instanceof FluidPipeBlock
                || block instanceof EncasedPipeBlock || block instanceof GlassFluidPipeBlock
                || block instanceof ICogWheel || block instanceof AbstractSimpleShaftBlock
                || block instanceof CasingBlock || block instanceof BlazeBurnerBlock
                || block instanceof CopycatBlock
                || SPENT_IRON.contains(block) || PERISHABLE.contains(block)
                || GLAZING.contains(block) || METALWORK.contains(block)
                || HARDWARE.contains(block) || METAL_SOLIDS.contains(block)
                || LOOSE_MACHINERY.contains(block) || ADDON_MACHINERY.contains(block)
                || DECORATIVE_FLAME.contains(block)
                || RAILS.contains(block) || isLevitite(block)
                || suffixed(block, CLOTH_SUFFIXES) || CLOTH_EXTRAS.contains(block)
                || suffixed(block, METAL_FITTING_SUFFIXES) || METAL_FITTING_EXTRAS.contains(block);
    }

    static Boolean isBurnerSpent(BlockState state) {
        if (!(state.getBlock() instanceof BlazeBurnerBlock)) {
            return null;
        }
        return state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.NONE;
    }

    private static boolean isEnvelope(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("aeronautics") && id.getPath().endsWith("_envelope");
    }
}
