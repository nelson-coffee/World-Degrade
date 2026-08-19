package dev.ncn.worlddegrade.compat.sable;

import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.compat.RunWork;
import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.undo.UndoSnapshot;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

public class SableCompat implements ModCompat {
    private static final int ON_LOAD_CHUNKS_PER_TICK = 2;
    private static final int ON_LOAD_REMOVALS_PER_TICK = 8;
    private static final int ON_LOAD_MAX_DEFERRALS = 200;

    private static final ArrayDeque<OnLoadShip> ON_LOAD_QUEUE = new ArrayDeque<>();

    private static final class OnLoadShip implements DegradeContext.RemovalSink {
        final ServerLevel level;
        final ServerSubLevel ship;
        final DegradeChances chances;
        final long seed;
        final ArrayDeque<ShipDegrader.PendingRemoval> pendingRemovals = new ArrayDeque<>();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet queuedRemovalPositions =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        ArrayDeque<ChunkPos> chunks;
        List<DegradeEffect> effects;
        UndoSnapshot discardedUndo;
        DegradeContext removalExecutor;
        int deferrals;

        OnLoadShip(ServerLevel level, ServerSubLevel ship, DegradeChances chances) {
            this.level = level;
            this.ship = ship;
            this.chances = chances;
            this.seed = level.getRandom().nextLong();
        }

        @Override
        public boolean enqueueRemoval(net.minecraft.core.BlockPos pos, boolean wipeContents) {
            if (!queuedRemovalPositions.add(pos.asLong())) {
                return false;
            }
            pendingRemovals.add(new ShipDegrader.PendingRemoval(pos.asLong(), wipeContents));
            return true;
        }
    }

    @Override
    public String modId() {
        return "sable";
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(this::onContainerReady);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new LevititeCrumbleEffect());
    }

    @Override
    public List<DegradeEffect> createShipOnlyEffects() {
        return List.of(new SableActorDecayEffect());
    }

    @Override
    public List<RunWork> createRunWork(ServerLevel level, DegradeArea area,
                                       DegradeChances chances, @Nullable UUID operator) {
        ShipDegrader degrader = new ShipDegrader(level, area, chances, operator);
        return degrader.hasShips() || area.isWholeDimension() ? List.of(degrader) : List.of();
    }

    @Override
    public boolean shouldRestore(ServerLevel level, BlockPos pos) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) {
            return true;
        }
        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null) {
            return false;
        }
        SubLevel subLevel = plot.getSubLevel();
        return subLevel != null && !subLevel.isRemoved();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        ON_LOAD_QUEUE.clear();
    }

    @Override
    public void onUndo(MinecraftServer server, CompoundTag compatSection) {
        if (compatSection.contains("pendingDimension")) {
            ResourceLocation dimension = ResourceLocation.parse(compatSection.getString("pendingDimension"));
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().equals(dimension)) {
                    ShipPendingDegradation.get(level).clearPending();
                }
            }
        }
    }

    private void onContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)
                || !(event.getContainer() instanceof ServerSubLevelContainer)) {
            return;
        }
        event.getContainer().addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelAdded(SubLevel subLevel) {
                if (!(subLevel instanceof ServerSubLevel ship)) {
                    return;
                }
                ShipPendingDegradation pending = ShipPendingDegradation.get(serverLevel);
                pending.markKnown(ship.getUniqueId());
                int levelId = pending.pendingLevelId();
                if (levelId > 0 && pending.claimOnAdd(ship.getUniqueId())) {
                    ON_LOAD_QUEUE.add(new OnLoadShip(serverLevel, ship, DegradeChances.of(DegradeLevel.byId(levelId))));
                }
            }
        });
    }

    private void onServerTick(ServerTickEvent.Post event) {
        OnLoadShip work = ON_LOAD_QUEUE.peek();
        if (work == null) {
            return;
        }
        if (work.ship.isRemoved()) {
            ON_LOAD_QUEUE.poll();
            return;
        }
        if (work.chunks == null) {
            var holders = work.ship.getPlot().getLoadedChunks();
            if (holders.isEmpty()) {
                ON_LOAD_QUEUE.poll();
                if (++work.deferrals < ON_LOAD_MAX_DEFERRALS) {
                    ON_LOAD_QUEUE.add(work);
                }
                return;
            }
            work.chunks = new ArrayDeque<>();
            for (PlotChunkHolder holder : holders) {
                if (holder.getChunk() != null) {
                    work.chunks.add(holder.getChunk().getPos());
                }
            }
            work.effects = CompatManager.createShipEffects();
            work.discardedUndo = UndoSnapshot.discarding(work.level.dimension());
            work.removalExecutor = new DegradeContext(work.level, work.chances,
                    work.discardedUndo, new long[0], work.seed);
        }
        if (!work.pendingRemovals.isEmpty()) {
            ShipDegrader.drainRemovals(work.pendingRemovals, work.removalExecutor, ON_LOAD_REMOVALS_PER_TICK);
            return;
        }
        for (int i = 0; i < ON_LOAD_CHUNKS_PER_TICK && !work.chunks.isEmpty() && work.pendingRemovals.isEmpty(); i++) {
            ShipDegrader.processShipChunk(work.level, work.ship, work.chunks.poll(),
                    work.effects, work.discardedUndo, work.chances, work.seed, work);
        }
        if (work.chunks.isEmpty() && work.pendingRemovals.isEmpty()) {
            ON_LOAD_QUEUE.poll();
        }
    }
}
