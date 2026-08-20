package dev.ncn.worlddegrade.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.compat.ModCompat;
import dev.ncn.worlddegrade.compat.RunWork;
import dev.ncn.worlddegrade.degrade.DegradeArea;
import dev.ncn.worlddegrade.degrade.DegradeChances;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.DegradeLevel;
import net.minecraft.core.BlockPos;
import dev.ncn.worlddegrade.degrade.effects.DegradeEffect;
import dev.ncn.worlddegrade.undo.UndoSnapshot;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public class CreateCompat implements ModCompat {
    private static final int ON_LOAD_DEFER_TICKS = 2;

    private static final ArrayDeque<OnLoadTarget> ON_LOAD_QUEUE = new ArrayDeque<>();

    private static final class OnLoadTarget {
        final ServerLevel level;
        final AbstractContraptionEntity entity;
        final com.simibubi.create.content.trains.entity.Train train;
        final DegradeChances chances;
        int deferrals;

        OnLoadTarget(ServerLevel level, AbstractContraptionEntity entity,
                     com.simibubi.create.content.trains.entity.Train train, DegradeChances chances) {
            this.level = level;
            this.entity = entity;
            this.train = train;
            this.chances = chances;
        }
    }

    @Override
    public String modId() {
        return "create";
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(this::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        SchematicannonTracker.register();
    }

    @Override
    public List<DegradeEffect> createEffects() {
        return List.of(new CreateLootEffect());
    }

    @Override
    public List<DegradeEffect> createWeatheringEffects() {
        return List.of(new CreateDecayEffect());
    }

    @Override
    public void registerWearSteps(BiConsumer<Block, Block> sink) {
        CreatePaletteWear.register(sink);
    }

    @Override
    public Boolean isFullyWorn(BlockState state) {
        return CreateDecayEffect.isBurnerSpent(state);
    }

    @Override
    public Boolean isFullyWorn(DegradeContext ctx, BlockPos pos, BlockState state) {
        Boolean copycat = CopycatDecay.isSpent(ctx, pos, state);
        return copycat != null ? copycat : CreateDecayEffect.isBurnerSpent(state);
    }

    @Override
    public List<RunWork> createRunWork(ServerLevel level, DegradeArea area,
                                       DegradeChances chances, @Nullable UUID operator) {
        ContraptionDegrader degrader = new ContraptionDegrader(level, area, chances, operator);
        return degrader.hasWork() || area.isWholeDimension() ? List.of(degrader) : List.of();
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        ON_LOAD_QUEUE.clear();
    }

    @Override
    public void onUndo(MinecraftServer server, CompoundTag compatSection) {
        ContraptionLootEffect.restore(server, compatSection);
        if (compatSection.contains("pendingDimension")) {
            ResourceLocation dimension = ResourceLocation.parse(compatSection.getString("pendingDimension"));
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().equals(dimension)) {
                    ContraptionPendingDegradation.get(level).clearPending();
                }
            }
        }
    }

    private void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof CarriageContraptionEntity carriageEntity) {
            Carriage carriage = carriageEntity.getCarriage();
            if (carriage == null || carriage.train == null) {
                return;
            }
            ContraptionPendingDegradation pending = ContraptionPendingDegradation.get(level);
            int levelId = pending.pendingLevelId();
            if (levelId > 0 && pending.claimTrain(carriage.train.id)) {
                ON_LOAD_QUEUE.add(new OnLoadTarget(level, null, carriage.train,
                        DegradeChances.of(DegradeLevel.byId(levelId))));
            }
        } else if (entity instanceof AbstractContraptionEntity contraption) {
            ContraptionPendingDegradation pending = ContraptionPendingDegradation.get(level);
            pending.markKnownContraption(contraption.getUUID());
            int levelId = pending.pendingLevelId();
            if (levelId > 0 && pending.claimContraption(contraption.getUUID())) {
                ON_LOAD_QUEUE.add(new OnLoadTarget(level, contraption, null,
                        DegradeChances.of(DegradeLevel.byId(levelId))));
            }
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        OnLoadTarget target = ON_LOAD_QUEUE.peek();
        if (target == null) {
            return;
        }
        if (target.deferrals < ON_LOAD_DEFER_TICKS) {
            target.deferrals++;
            return;
        }
        ON_LOAD_QUEUE.poll();
        UndoSnapshot discarded = UndoSnapshot.discarding(target.level.dimension());
        List<DegradeEffect> effects = CompatManager.createEffects();
        long seed = target.level.getRandom().nextLong();
        LongOpenHashSet materialized = target.train != null
                ? ContraptionDegrader.wreckTrain(target.level, target.train)
                : ContraptionDegrader.disassembleContraption(target.level, target.entity);
        ContraptionDegrader.degradeMaterialized(target.level, materialized, effects, discarded, target.chances, seed);
    }
}
