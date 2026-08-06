package dev.ncn.worlddegrade.undo;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeJob;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class UndoManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "worlddegrade_undo.dat";
    private static final int BLOCKS_PER_TICK = 4096;

    private static UndoSnapshot latest;
    private static RestoreJob restoreJob;

    public static void beginRun(ServerLevel level) {
        latest = new UndoSnapshot(level.dimension());
    }

    public static UndoSnapshot current() {
        return latest;
    }

    public static boolean isRestoring() {
        return restoreJob != null;
    }

    public static void finishRun(MinecraftServer server) {
        if (latest == null) {
            return;
        }
        try {
            Path path = undoFile(server);
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(latest.toNbt(), path);
            latest = null;
        } catch (IOException e) {
            LOGGER.error("Failed to write degradation undo snapshot; /degrade undo will only work until restart", e);
        }
    }

    public static void shutdown() {
        if (restoreJob != null) {
            while (!restoreJob.tick()) {
                // The snapshot file is deleted when a restore starts, so an unfinished
                // one cannot be resumed — drain it rather than leave the world half-reverted.
            }
            restoreJob = null;
        }
        latest = null;
    }

    public static int undo(CommandSourceStack source) {
        if (DegradeJob.isBusy() || isRestoring()) {
            source.sendFailure(Component.translatable("chat.worlddegrade.busy"));
            return 0;
        }
        if (latest == null) {
            latest = loadFromDisk(source.getServer());
        }
        if (latest == null || latest.isEmpty()) {
            source.sendFailure(Component.translatable("chat.worlddegrade.noundo"));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(latest.dimension());
        if (level == null) {
            source.sendFailure(Component.translatable("chat.worlddegrade.noundo"));
            return 0;
        }
        restoreJob = new RestoreJob(level, latest, source);
        latest = null;
        deleteFile(source.getServer());
        source.sendSuccess(() -> Component.translatable("chat.worlddegrade.undo.start", restoreJob.records.size()), true);
        return Command.SINGLE_SUCCESS;
    }

    public static void tickRestore(MinecraftServer server) {
        if (restoreJob != null && restoreJob.tick()) {
            restoreJob = null;
        }
    }

    private static UndoSnapshot loadFromDisk(MinecraftServer server) {
        Path path = undoFile(server);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return UndoSnapshot.fromNbt(tag);
        } catch (IOException e) {
            LOGGER.error("Failed to read degradation undo snapshot", e);
            return null;
        }
    }

    private static void deleteFile(MinecraftServer server) {
        try {
            Files.deleteIfExists(undoFile(server));
        } catch (IOException e) {
            LOGGER.error("Failed to delete degradation undo snapshot", e);
        }
    }

    private static Path undoFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
    }

    private static final class RestoreJob {
        private final ServerLevel level;
        private final UndoSnapshot snapshot;
        private final List<UndoSnapshot.BlockRecord> records;
        private final CommandSourceStack source;
        private int cursor;

        RestoreJob(ServerLevel level, UndoSnapshot snapshot, CommandSourceStack source) {
            this.level = level;
            this.snapshot = snapshot;
            this.records = snapshot.allRecords();
            this.source = source;
        }

        boolean tick() {
            int end = Math.min(cursor + BLOCKS_PER_TICK, records.size());
            for (; cursor < end; cursor++) {
                restore(records.get(cursor));
            }
            if (cursor >= records.size()) {
                CompatManager.onUndo(level.getServer(), snapshot::compatSection);
                source.sendSuccess(() -> Component.translatable("chat.worlddegrade.undo.done", records.size()), true);
                return true;
            }
            return false;
        }

        private void restore(UndoSnapshot.BlockRecord record) {
            BlockPos pos = BlockPos.of(record.pos());
            if (!CompatManager.shouldRestore(level, pos)) {
                return;
            }
            BlockState state = NbtUtils.readBlockState(
                    level.holderLookup(Registries.BLOCK), record.stateTag());
            if (!state.is(level.getBlockState(pos).getBlock()) && level.getBlockEntity(pos) != null) {
                level.removeBlockEntity(pos);
            }
            level.setBlock(pos, state, DegradeContext.SET_BLOCK_FLAGS);
            if (record.blockEntityTag() != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(record.blockEntityTag(), level.registryAccess());
                    blockEntity.setChanged();
                }
            }
            if (state.isAir()) {
                PlacementTracker.untrack(level, pos);
            } else {
                PlacementTracker.track(level, pos);
            }
        }
    }

    private UndoManager() {
    }
}
