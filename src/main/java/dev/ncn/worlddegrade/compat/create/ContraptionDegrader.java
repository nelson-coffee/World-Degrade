package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.compat.RunWork;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import dev.ncn.worlddegrade.undo.UndoManager;
import dev.ncn.worlddegrade.undo.UndoSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ContraptionDegrader implements RunWork {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final int DISASSEMBLE_PER_TICK = 2;
    private static final int CHUNKS_PER_TICK = 4;
    private static final int BOX_MARGIN = 2;

    private final ServerLevel level;
    private final DegradeChances chances;
    private final boolean wholeWorld;
    private final UUID operatorId;
    private final long runSeed;

    private final Deque<AbstractContraptionEntity> nonTrainQueue = new ArrayDeque<>();
    private final Deque<Train> trainQueue = new ArrayDeque<>();
    private final Set<UUID> reachedContraptionIds = new HashSet<>();
    private final Set<UUID> reachedTrainIds = new HashSet<>();
    private final int totalTargets;

    private final LongOpenHashSet materialized = new LongOpenHashSet();
    private final Deque<long[]> degradeQueue = new ArrayDeque<>();
    private List<DegradeEffect> effects;
    private boolean started;
    private boolean bucketed;
    private int changedBlocks;

    public ContraptionDegrader(ServerPlayer operator, DegradeChances chances, boolean wholeWorld, int radius) {
        this.level = operator.serverLevel();
        this.chances = chances;
        this.wholeWorld = wholeWorld;
        this.operatorId = operator.getUUID();
        this.runSeed = level.getRandom().nextLong();

        AABB scanBox = wholeWorld
                ? new AABB(-30_000_000, level.getMinBuildHeight(), -30_000_000,
                        30_000_000, level.getMaxBuildHeight(), 30_000_000)
                : new AABB(operator.getX() - radius, level.getMinBuildHeight(), operator.getZ() - radius,
                        operator.getX() + radius, level.getMaxBuildHeight(), operator.getZ() + radius);
        List<AbstractContraptionEntity> found = level.getEntitiesOfClass(
                AbstractContraptionEntity.class, scanBox, e -> e.isAlive());

        Map<UUID, Train> trains = new HashMap<>();
        for (AbstractContraptionEntity entity : found) {
            if (entity instanceof CarriageContraptionEntity carriageEntity) {
                Carriage carriage = carriageEntity.getCarriage();
                if (carriage != null && carriage.train != null) {
                    trains.putIfAbsent(carriage.train.id, carriage.train);
                }
            } else {
                nonTrainQueue.add(entity);
                reachedContraptionIds.add(entity.getUUID());
            }
        }
        trainQueue.addAll(trains.values());
        reachedTrainIds.addAll(trains.keySet());
        this.totalTargets = nonTrainQueue.size() + trainQueue.size();
    }

    public boolean hasWork() {
        return totalTargets > 0;
    }

    @Override
    public boolean tick() {
        if (!started) {
            started = true;
            start();
        }
        if (!nonTrainQueue.isEmpty() || !trainQueue.isEmpty()) {
            for (int i = 0; i < DISASSEMBLE_PER_TICK && (!nonTrainQueue.isEmpty() || !trainQueue.isEmpty()); i++) {
                if (!nonTrainQueue.isEmpty()) {
                    materialized.addAll(disassembleContraption(level, nonTrainQueue.poll()));
                } else {
                    materialized.addAll(wreckTrain(level, trainQueue.poll()));
                }
            }
            return false;
        }
        if (!bucketed) {
            bucketed = true;
            for (long[] bucket : bucketByChunk(materialized)) {
                degradeQueue.add(bucket);
            }
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !degradeQueue.isEmpty(); i++) {
            changedBlocks += degradePositions(level, degradeQueue.poll(), effects,
                    UndoManager.current(), chances, runSeed);
        }
        return degradeQueue.isEmpty();
    }

    @Override
    public int changedBlocks() {
        return changedBlocks;
    }

    private void start() {
        this.effects = CompatManager.createEffects();
        if (wholeWorld) {
            markPending();
        }
        ServerPlayer operator = level.getServer().getPlayerList().getPlayer(operatorId);
        if (operator != null && totalTargets > 0) {
            operator.sendSystemMessage(Component.translatable("chat.worlddegrade.contraptions", totalTargets));
        }
    }

    private void markPending() {
        ContraptionPendingDegradation pending = ContraptionPendingDegradation.get(level);
        Set<UUID> eligibleContraptions = pending.knownContraptionsSnapshot();
        for (UUID id : reachedContraptionIds) {
            pending.markKnownContraption(id);
            eligibleContraptions.remove(id);
        }
        Set<UUID> eligibleTrains = new HashSet<>(Create.RAILWAYS.trains.keySet());
        eligibleTrains.removeAll(reachedTrainIds);
        pending.setPending(chances.levelId(), eligibleContraptions, eligibleTrains);
        UndoManager.current().compatSection("create")
                .putString("pendingDimension", level.dimension().location().toString());
    }

    static LongOpenHashSet disassembleContraption(ServerLevel level, AbstractContraptionEntity entity) {
        if (!entity.isAlive()) {
            return new LongOpenHashSet();
        }
        return collectMaterialized(level, rotationSafeScanBox(entity), entity::disassemble);
    }

    static AABB rotationSafeScanBox(AbstractContraptionEntity entity) {
        Contraption contraption = entity.getContraption();
        if (contraption == null || contraption.bounds == null) {
            return entity.getBoundingBox();
        }
        AABB local = contraption.bounds;
        double ax = Math.max(Math.abs(local.minX), Math.abs(local.maxX));
        double ay = Math.max(Math.abs(local.minY), Math.abs(local.maxY));
        double az = Math.max(Math.abs(local.minZ), Math.abs(local.maxZ));
        double radius = Math.ceil(Math.sqrt(ax * ax + ay * ay + az * az));
        Vec3 center = entity.getAnchorVec();
        return new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
    }

    static LongOpenHashSet wreckTrain(ServerLevel level, Train train) {
        AABB bounds = TrainWrecker.carriageBounds(train);
        if (bounds == null) {
            return new LongOpenHashSet();
        }
        return collectMaterialized(level, bounds, () -> {
            if (!TrainWrecker.teardown(train)) {
                LOGGER.warn("World Degrade: train {} could not be torn down; any blocks it did "
                        + "materialise are still degraded", train.id);
            }
        });
    }

    private static LongOpenHashSet collectMaterialized(ServerLevel level, AABB box, Runnable action) {
        int minX = Mth.floor(box.minX) - BOX_MARGIN;
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(box.minY) - BOX_MARGIN);
        int minZ = Mth.floor(box.minZ) - BOX_MARGIN;
        int maxX = Mth.floor(box.maxX) + BOX_MARGIN;
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(box.maxY) + BOX_MARGIN);
        int maxZ = Mth.floor(box.maxZ) + BOX_MARGIN;

        Long2ObjectMap<BlockState> before = new Long2ObjectOpenHashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!state.isAir()) {
                        before.put(cursor.asLong(), state);
                    }
                }
            }
        }

        action.run();

        LongOpenHashSet result = new LongOpenHashSet();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState after = level.getBlockState(cursor);
                    if (after.isAir() || after == before.getOrDefault(cursor.asLong(), air)) {
                        continue;
                    }
                    result.add(cursor.asLong());
                    PlacementTracker.track(level, cursor);
                }
            }
        }
        return result;
    }

    static int degradeMaterialized(ServerLevel level, LongOpenHashSet positions, List<DegradeEffect> effects,
                                   UndoSnapshot undo, DegradeChances chances, long seed) {
        int changed = 0;
        for (long[] bucket : bucketByChunk(positions)) {
            changed += degradePositions(level, bucket, effects, undo, chances, seed);
        }
        return changed;
    }

    private static int degradePositions(ServerLevel level, long[] positions, List<DegradeEffect> effects,
                                        UndoSnapshot undo, DegradeChances chances, long seed) {
        if (positions.length == 0) {
            return 0;
        }
        DegradeContext context = new DegradeContext(level, chances, undo, positions, seed);
        for (DegradeEffect effect : effects) {
            effect.apply(context);
        }
        return context.changedBlocks();
    }

    private static List<long[]> bucketByChunk(LongOpenHashSet positions) {
        Long2ObjectMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
        for (long packed : positions) {
            long chunkKey = ChunkPos.asLong(BlockPos.getX(packed) >> 4, BlockPos.getZ(packed) >> 4);
            byChunk.computeIfAbsent(chunkKey, k -> new LongArrayList()).add(packed);
        }
        List<long[]> buckets = new ArrayList<>(byChunk.size());
        for (LongArrayList list : byChunk.values()) {
            buckets.add(list.toLongArray());
        }
        return buckets;
    }
}
