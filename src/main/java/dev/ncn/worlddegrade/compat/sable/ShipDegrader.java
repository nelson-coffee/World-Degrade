package dev.ncn.worlddegrade.compat.sable;

import com.mojang.serialization.Codec;
import dev.ncn.worlddegrade.WorldDegrade;
import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.compat.RunWork;
import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.undo.UndoManager;
import dev.ncn.worlddegrade.undo.UndoSnapshot;
import org.jetbrains.annotations.Nullable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShipDegrader implements RunWork, DegradeContext.RemovalSink {
    private static final int CHUNKS_PER_TICK = 2;
    private static final int REMOVALS_PER_TICK = 8;

    private static final SubLevelLoadingTicketType<Unit> DEGRADING_TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath(WorldDegrade.MOD_ID, "degrading"), Codec.unit(Unit.INSTANCE));

    private record ShipChunk(ServerSubLevel ship, ChunkPos pos) {
    }

    record PendingRemoval(long pos, boolean wipeContents) {
    }

    private final ServerLevel level;
    private final ServerSubLevelContainer container;
    private final DegradeChances chances;
    @Nullable
    private final UUID operatorId;
    private final boolean wholeWorld;
    private final List<ServerSubLevel> ships = new ArrayList<>();
    private final List<ServerSubLevel> ticketedShips = new ArrayList<>();
    private final ArrayDeque<ShipChunk> queue = new ArrayDeque<>();
    private final ArrayDeque<PendingRemoval> pendingRemovals = new ArrayDeque<>();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet queuedRemovalPositions =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final long runSeed;
    private List<DegradeEffect> effects;
    private DegradeContext removalExecutor;
    private boolean started;
    private int changedBlocks;

    public ShipDegrader(ServerLevel level, DegradeArea area, DegradeChances chances,
                        @Nullable UUID operator) {
        this.level = level;
        this.container = SubLevelContainer.getContainer(this.level);
        this.chances = chances;
        this.operatorId = operator;
        this.wholeWorld = area.isWholeDimension();
        this.runSeed = this.level.getRandom().nextLong();
        if (container == null) {
            return;
        }
        if (wholeWorld) {
            ships.addAll(container.getAllSubLevels());
        } else {
            // queryIntersecting only sees the coarse hull, which for a disjoint Chunks area covers
            // every column between the claims. Re-filter on each ship's anchor column.
            AABB scanBox = area.scanBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
            for (SubLevel subLevel : container.queryIntersecting(new BoundingBox3d(scanBox))) {
                if (subLevel instanceof ServerSubLevel serverSubLevel && isAnchoredInArea(subLevel, area)) {
                    ships.add(serverSubLevel);
                }
            }
        }
        ships.removeIf(SubLevel::isRemoved);
    }

    public boolean hasShips() {
        return !ships.isEmpty();
    }

    @Override
    public int targetCount() {
        return ships.size();
    }

    private static boolean isAnchoredInArea(SubLevel subLevel, DegradeArea area) {
        Vector3dc anchor = subLevel.logicalPose().position();
        return area.containsColumn(anchor.x(), anchor.z());
    }

    @Override
    public boolean tick() {
        if (!started) {
            started = true;
            start();
        }
        if (!pendingRemovals.isEmpty()) {
            drainRemovals(pendingRemovals, removalExecutor, REMOVALS_PER_TICK);
            return false;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty() && pendingRemovals.isEmpty(); i++) {
            processChunk(queue.poll());
        }
        if (queue.isEmpty() && pendingRemovals.isEmpty()) {
            for (ServerSubLevel ship : ticketedShips) {
                container.removeForceLoadTicket(ship, DEGRADING_TICKET, Unit.INSTANCE);
            }
            ticketedShips.clear();
            return true;
        }
        return false;
    }

    @Override
    public boolean enqueueRemoval(BlockPos pos, boolean wipeContents) {
        if (!queuedRemovalPositions.add(pos.asLong())) {
            return false;
        }
        pendingRemovals.add(new PendingRemoval(pos.asLong(), wipeContents));
        return true;
    }

    static void drainRemovals(ArrayDeque<PendingRemoval> removals, DegradeContext executor, int budget) {
        for (int i = 0; i < budget && !removals.isEmpty(); i++) {
            PendingRemoval removal = removals.poll();
            BlockPos pos = BlockPos.of(removal.pos());
            if (removal.wipeContents()) {
                executor.removeBlockAndWipeContents(pos);
            } else {
                executor.removeBlock(pos);
            }
        }
    }

    @Override
    public int changedBlocks() {
        return changedBlocks;
    }

    private void start() {
        this.effects = CompatManager.createShipEffects();
        this.removalExecutor = new DegradeContext(level, chances, UndoManager.current(), new long[0], runSeed);
        for (ServerSubLevel ship : ships) {
            if (ship.isRemoved()) {
                continue;
            }
            List<PlotChunkHolder> holders = new ArrayList<>(ship.getPlot().getLoadedChunks());
            if (holders.isEmpty()) {
                continue;
            }
            container.addForceLoadTicket(ship, DEGRADING_TICKET, Unit.INSTANCE);
            ticketedShips.add(ship);
            for (PlotChunkHolder holder : holders) {
                LevelChunk chunk = holder.getChunk();
                if (chunk != null) {
                    queue.add(new ShipChunk(ship, chunk.getPos()));
                }
            }
        }
        if (wholeWorld) {
            ShipPendingDegradation pending = ShipPendingDegradation.get(level);
            java.util.Set<UUID> eligible = pending.knownShipsSnapshot();
            for (ServerSubLevel ship : ships) {
                pending.markKnown(ship.getUniqueId());
                eligible.remove(ship.getUniqueId());
            }
            pending.setPending(chances.levelId(), eligible);
            UndoManager.current().compatSection("sable")
                    .putString("pendingDimension", level.dimension().location().toString());
        }
        if (operatorId != null && !ships.isEmpty()) {
            ServerPlayer operator = level.getServer().getPlayerList().getPlayer(operatorId);
            if (operator != null) {
                operator.sendSystemMessage(Component.translatable("chat.worlddegrade.ships", ships.size()));
            }
        }
    }

    private void processChunk(ShipChunk shipChunk) {
        changedBlocks += processShipChunk(level, shipChunk.ship(), shipChunk.pos(),
                effects, UndoManager.current(), chances, runSeed, this);
    }

    static int processShipChunk(ServerLevel level, ServerSubLevel ship, ChunkPos pos,
                                List<DegradeEffect> effects, UndoSnapshot undo,
                                DegradeChances chances, long runSeed,
                                DegradeContext.RemovalSink removalSink) {
        if (ship.isRemoved()) {
            return 0;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        LevelChunk chunk = container != null ? container.getChunk(pos) : null;
        if (chunk == null) {
            return 0;
        }
        long[] positions = collectNonAirPositions(chunk);
        if (positions.length == 0) {
            return 0;
        }
        DegradeContext context = new DegradeContext(level, chances, undo, positions, runSeed);
        context.deferRemovalsTo(removalSink);
        context.setDeferStructureToCollapse(false);
        for (DegradeEffect effect : effects) {
            effect.apply(context);
        }
        chunk.setUnsaved(true);
        return context.changedBlocks();
    }

    private static long[] collectNonAirPositions(LevelChunk chunk) {
        LongArrayList positions = new LongArrayList();
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).isAir()) {
                            positions.add(BlockPos.asLong(
                                    chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
        return positions.toLongArray();
    }
}
