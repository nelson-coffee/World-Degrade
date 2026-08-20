package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.BrickWeatherEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

final class CopycatDecay {

    private CopycatDecay() {
    }

    static void decay(DegradeContext ctx, BlockPos pos, BlockState state, float machineChance) {
        BlockState material = material(ctx, pos);
        if (material == null) {
            if (ctx.roll(machineChance)) {
                ctx.removeBlockAndWipeContents(pos);
            }
            return;
        }
        BlockState worn = BrickWeatherEffect.weather(material);
        if (worn == null || BrickWeatherEffect.isFullyWorn(material)) {
            if (ctx.roll(machineChance)) {
                ctx.removeBlockAndWipeContents(pos);
            }
            return;
        }
        if (!ctx.roll(ctx.patchChance(pos, ctx.chances.brickWeatherChance()))) {
            return;
        }
        setMaterial(ctx, pos, worn);
    }

    @Nullable
    static BlockState material(DegradeContext ctx, BlockPos pos) {
        if (!(ctx.blockEntity(pos) instanceof CopycatBlockEntity copycat)) {
            return null;
        }
        if (!copycat.hasCustomMaterial()) {
            return null;
        }
        BlockState material = copycat.getMaterial();
        return material == null || material.isAir() ? null : material;
    }

    private static void setMaterial(DegradeContext ctx, BlockPos pos, BlockState worn) {
        if (ctx.isExempt(pos)) {
            return;
        }
        if (!(ctx.blockEntity(pos) instanceof CopycatBlockEntity copycat)) {
            return;
        }
        ctx.recordForUndo(pos);
        copycat.setMaterial(worn);
        copycat.setChanged();
        BlockState state = ctx.state(pos);
        ctx.level.sendBlockUpdated(pos, state, state, DegradeContext.SET_BLOCK_FLAGS);
        ctx.markChanged();
    }

    @Nullable
    static Boolean isSpent(DegradeContext ctx, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CopycatBlock)) {
            return null;
        }
        BlockState material = material(ctx, pos);
        return material == null || BrickWeatherEffect.isFullyWorn(material);
    }
}
