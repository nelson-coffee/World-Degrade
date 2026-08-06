package dev.ncn.worlddegrade.block;

import dev.ncn.worlddegrade.WorldDegrade;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WorldDegrade.MOD_ID);

    public static final DeferredBlock<Block> BURNT_TORCH = BLOCKS.register("burnt_torch",
            () -> new BurntTorchBlock(() -> Blocks.TORCH, torchProperties()));
    public static final DeferredBlock<Block> BURNT_WALL_TORCH = BLOCKS.register("burnt_wall_torch",
            () -> new BurntWallTorchBlock(() -> Blocks.WALL_TORCH,
                    torchProperties().dropsLike(BURNT_TORCH.get())));
    public static final DeferredBlock<Block> BURNT_SOUL_TORCH = BLOCKS.register("burnt_soul_torch",
            () -> new BurntTorchBlock(() -> Blocks.SOUL_TORCH, torchProperties()));
    public static final DeferredBlock<Block> BURNT_SOUL_WALL_TORCH = BLOCKS.register("burnt_soul_wall_torch",
            () -> new BurntWallTorchBlock(() -> Blocks.SOUL_WALL_TORCH,
                    torchProperties().dropsLike(BURNT_SOUL_TORCH.get())));

    public static final DeferredBlock<Block> BURNT_LANTERN = BLOCKS.register("burnt_lantern",
            () -> new BurntLanternBlock(() -> Blocks.LANTERN, lanternProperties()));
    public static final DeferredBlock<Block> BURNT_SOUL_LANTERN = BLOCKS.register("burnt_soul_lantern",
            () -> new BurntLanternBlock(() -> Blocks.SOUL_LANTERN, lanternProperties()));

    private static BlockBehaviour.Properties lanternProperties() {
        return BlockBehaviour.Properties.of()
                .requiresCorrectToolForDrops()
                .strength(3.5f)
                .sound(SoundType.LANTERN)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties torchProperties() {
        return BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY);
    }

    private ModBlocks() {
    }
}
