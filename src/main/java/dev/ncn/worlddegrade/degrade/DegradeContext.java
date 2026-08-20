package dev.ncn.worlddegrade.degrade;

import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import dev.ncn.worlddegrade.undo.UndoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

public class DegradeContext {
    public final ServerLevel level;
    public final DegradeChances chances;
    public final RandomSource random;

    private final long runSeed;
    private final UndoSnapshot undo;
    private final long[] positions;
    private final boolean protectFilledContainers;
    private int changedBlocks;

    public DegradeContext(ServerLevel level, DegradeChances chances, UndoSnapshot undo,
                          long[] positions, long runSeed) {
        this.level = level;
        this.chances = chances;
        this.random = level.getRandom();
        this.undo = undo;
        this.positions = positions;
        this.runSeed = runSeed;
        this.protectFilledContainers = WorldDegradeConfig.protectFilledContainersEnabled();
    }

    public long[] positions() {
        return positions;
    }

    public BlockState state(BlockPos pos) {
        return level.getBlockState(pos);
    }

    @Nullable
    public BlockEntity blockEntity(BlockPos pos) {
        return level.getBlockEntity(pos);
    }

    public boolean roll(float chance) {
        return random.nextFloat() < chance;
    }

    public float patchChance(BlockPos pos, float base) {
        long hash = Mth.getSeed(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2) ^ runSeed;
        hash *= 0x9E3779B97F4A7C15L;
        float cell = ((hash >>> 40) & 0xFFFFFF) / (float) (1 << 24);
        return Mth.clamp(base * (0.25f + 1.75f * cell), 0.0f, 1.0f);
    }

    public float noise2d(int x, int z, int cellSize) {
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        float fx = smoothstep((x - cellX * cellSize) / (float) cellSize);
        float fz = smoothstep((z - cellZ * cellSize) / (float) cellSize);
        float top = Mth.lerp(fx, lattice(cellX, cellZ), lattice(cellX + 1, cellZ));
        float bottom = Mth.lerp(fx, lattice(cellX, cellZ + 1), lattice(cellX + 1, cellZ + 1));
        return Mth.clamp(Mth.lerp(fz, top, bottom), 0.0f, 1.0f);
    }

    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private float lattice(int cellX, int cellZ) {
        long hash = (Mth.getSeed(cellX, 0, cellZ) ^ runSeed) * 0x9E3779B97F4A7C15L;
        return ((hash >>> 40) & 0xFFFFFF) / (float) (1 << 24);
    }

    public static final int SET_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public interface RemovalSink {
        boolean enqueueRemoval(BlockPos pos, boolean wipeContents);
    }

    @Nullable
    private RemovalSink removalSink;

    public void deferRemovalsTo(RemovalSink sink) {
        this.removalSink = sink;
    }

    public void recordForUndo(BlockPos pos) {
        undo.record(level, pos);
    }

    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet lootedPositions =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public boolean claimLoot(BlockPos pos) {
        return lootedPositions.add(pos.asLong());
    }

    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet claimedPositions =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public void claim(BlockPos pos) {
        claimedPositions.add(pos.asLong());
    }

    public boolean isClaimed(BlockPos pos) {
        return claimedPositions.contains(pos.asLong());
    }

    private boolean deferStructureToCollapse = true;

    public boolean deferStructureToCollapse() {
        return deferStructureToCollapse;
    }

    public void setDeferStructureToCollapse(boolean defer) {
        this.deferStructureToCollapse = defer;
    }

    private boolean isUnbreakable(BlockPos pos) {
        BlockState state = state(pos);
        if (state.is(Blocks.NETHER_PORTAL)) {
            return false;
        }
        return state.getDestroySpeed(level, pos) < 0;
    }

    private boolean holdsItems(BlockPos pos) {
        if (!protectFilledContainers) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        if (blockEntity instanceof Container container) {
            return !container.isEmpty() || partnerHoldsItems(pos);
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean partnerHoldsItems(BlockPos pos) {
        BlockPos partner = chestPartner(pos, state(pos));
        return partner != null
                && level.getBlockEntity(partner) instanceof Container container
                && !container.isEmpty();
    }

    @Nullable
    private BlockPos chestPartner(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState partnerState = state(partner);
        if (!partnerState.is(state.getBlock()) || !partnerState.hasProperty(ChestBlock.TYPE)
                || partnerState.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || !partner.relative(ChestBlock.getConnectedDirection(partnerState)).equals(pos)) {
            return null;
        }
        return partner;
    }

    private void detachChestPartner(BlockPos pos, BlockState removedState) {
        BlockPos partner = chestPartner(pos, removedState);
        if (partner == null) {
            return;
        }
        undo.record(level, partner);
        level.setBlock(partner, state(partner).setValue(ChestBlock.TYPE, ChestType.SINGLE),
                SET_BLOCK_FLAGS);
    }

    public boolean normalizeOrphanedChest(BlockPos pos) {
        BlockState state = state(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || chestPartner(pos, state) != null) {
            return false;
        }
        undo.record(level, pos);
        level.setBlock(pos, state.setValue(ChestBlock.TYPE, ChestType.SINGLE), SET_BLOCK_FLAGS);
        changedBlocks++;
        return true;
    }

    public boolean removeBlock(BlockPos pos) {
        if (isUnbreakable(pos) || holdsItems(pos)) {
            return false;
        }
        if (removalSink != null) {
            if (removalSink.enqueueRemoval(pos, false)) {
                changedBlocks++;
            }
            return true;
        }
        undo.record(level, pos);
        BlockState removed = state(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), SET_BLOCK_FLAGS);
        detachChestPartner(pos, removed);
        PlacementTracker.untrack(level, pos);
        changedBlocks++;
        return true;
    }

    public void replaceBlock(BlockPos pos, BlockState newState) {
        if (isUnbreakable(pos) || holdsItems(pos)) {
            return;
        }
        undo.record(level, pos);
        level.setBlock(pos, newState, SET_BLOCK_FLAGS);
        changedBlocks++;
    }

    public void replaceBlockPreservingEntity(BlockPos pos, BlockState newState) {
        if (isUnbreakable(pos)) {
            return;
        }
        undo.record(level, pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag saved = blockEntity != null
                ? blockEntity.saveWithoutMetadata(level.registryAccess())
                : null;
        if (blockEntity != null) {
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, newState, SET_BLOCK_FLAGS);
        if (saved != null) {
            BlockEntity replacement = level.getBlockEntity(pos);
            if (replacement != null) {
                replacement.loadWithComponents(saved, level.registryAccess());
                replacement.setChanged();
            }
        }
        changedBlocks++;
    }

    public void replaceBlockDiscardingEntity(BlockPos pos, BlockState newState) {
        if (isUnbreakable(pos) || holdsItems(pos)) {
            return;
        }
        undo.record(level, pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Clearable clearable) {
            clearable.clearContent();
        }
        if (blockEntity != null) {
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, newState, SET_BLOCK_FLAGS);
        changedBlocks++;
    }

    public void placeExtra(BlockPos pos, BlockState state) {
        if (isUnbreakable(pos)) {
            return;
        }
        undo.record(level, pos);
        level.setBlock(pos, state, SET_BLOCK_FLAGS);
        PlacementTracker.track(level, pos);
        changedBlocks++;
    }

    public void dropItem(BlockPos pos, ItemStack stack) {
        Block.popResource(level, pos, stack);
    }

    public boolean removeBlockAndWipeContents(BlockPos pos) {
        if (isUnbreakable(pos) || holdsItems(pos)) {
            return false;
        }
        if (removalSink != null) {
            if (removalSink.enqueueRemoval(pos, true)) {
                changedBlocks++;
            }
            return true;
        }
        undo.record(level, pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Clearable clearable) {
            clearable.clearContent();
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler instanceof IItemHandlerModifiable modifiable) {
            for (int slot = 0; slot < modifiable.getSlots(); slot++) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        BlockState removed = state(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), SET_BLOCK_FLAGS);
        detachChestPartner(pos, removed);
        PlacementTracker.untrack(level, pos);
        changedBlocks++;
        return true;
    }

    private it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap excavatedCeilings =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    public void setExcavatedCeilings(it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap ceilings) {
        this.excavatedCeilings = ceilings;
    }

    public int excavationAllowance(BlockPos pos) {
        return excavatedCeilings.get(pos.asLong());
    }

    public boolean scatterDebrisOrKeep(BlockPos origin, BlockState sourceState, int searchDepth) {
        BlockPos.MutableBlockPos cursor = origin.mutable().move(0, -1, 0);
        for (int i = 0; i < searchDepth && cursor.getY() > level.getMinBuildHeight(); i++) {
            if (!state(cursor.below()).isAir()) {
                if (!state(cursor).isAir()) {
                    return false;
                }
                placeExtra(cursor.immutable(), sourceState.getBlock().defaultBlockState());
                return true;
            }
            cursor.move(0, -1, 0);
        }
        return false;
    }

    public void scatterDebris(BlockPos origin, BlockState sourceState, int searchDepth) {
        BlockPos.MutableBlockPos cursor = origin.mutable().move(0, -1, 0);
        for (int i = 0; i < searchDepth && cursor.getY() > level.getMinBuildHeight(); i++) {
            if (!state(cursor.below()).isAir()) {
                if (state(cursor).isAir()) {
                    placeExtra(cursor.immutable(), sourceState.getBlock().defaultBlockState());
                } else {
                    dropItem(cursor.immutable(), new ItemStack(sourceState.getBlock()));
                }
                return;
            }
            cursor.move(0, -1, 0);
        }
    }

    public CompoundTag undoCompatSection(String key) {
        return undo.compatSection(key);
    }

    public void markChanged() {
        changedBlocks++;
    }

    public int changedBlocks() {
        return changedBlocks;
    }
}
