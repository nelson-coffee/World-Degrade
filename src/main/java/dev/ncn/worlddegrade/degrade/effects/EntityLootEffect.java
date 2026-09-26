package dev.ncn.worlddegrade.degrade.effects;

import dev.ncn.worlddegrade.compat.CompatManager;
import dev.ncn.worlddegrade.degrade.DegradeContext;
import dev.ncn.worlddegrade.degrade.NestedItems;
import dev.ncn.worlddegrade.marking.MarkedRegions;
import dev.ncn.worlddegrade.tracking.PlacementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class EntityLootEffect implements DegradeEffect {
    public static final String UNDO_KEY = "entityloot";

    private final Set<UUID> visited = new HashSet<>();
    private final boolean enabled;

    public EntityLootEffect(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean shipSafe() {
        return false;
    }

    @Override
    public void apply(DegradeContext ctx) {
        if (!enabled) {
            return;
        }
        long[] positions = ctx.positions();
        if (positions.length == 0) {
            return;
        }
        BlockPos sample = BlockPos.of(positions[0]);
        int minX = SectionPos.blockToSectionCoord(sample.getX()) << 4;
        int minZ = SectionPos.blockToSectionCoord(sample.getZ()) << 4;
        AABB bounds = new AABB(minX, ctx.level.getMinBuildHeight(), minZ,
                minX + 16, ctx.level.getMaxBuildHeight(), minZ + 16);

        for (Entity entity : ctx.level.getEntities((Entity) null, bounds, EntityLootEffect::isTarget)) {
            if (!visited.add(entity.getUUID()) || !isPlayerMade(ctx, entity)) {
                continue;
            }
            if (entity instanceof ItemFrame frame) {
                lootFrame(ctx, frame);
            } else if (entity instanceof ArmorStand stand) {
                lootArmorStand(ctx, stand);
            } else if (entity instanceof AbstractHorse horse) {
                lootChestedAnimal(ctx, horse);
            } else if (entity instanceof Container container) {
                lootContainerEntity(ctx, entity, container);
            } else {
                lootViaCompat(ctx, entity);
            }
        }
    }

    private static void lootViaCompat(DegradeContext ctx, Entity entity) {
        EntityLooter looter = looterFor(entity);
        if (looter == null) {
            return;
        }
        boolean[] recorded = {false};
        Runnable recordUndo = () -> {
            if (!recorded[0]) {
                recorded[0] = true;
                record(ctx, entity, false);
            }
        };
        if (looter.loot(ctx, entity, recordUndo)) {
            ctx.markChanged();
        }
    }

    private static EntityLooter looterFor(Entity entity) {
        for (EntityLooter looter : CompatManager.entityLooters()) {
            if (looter.handles(entity)) {
                return looter;
            }
        }
        return null;
    }

    private static boolean isTarget(Entity entity) {
        return entity instanceof ItemFrame || entity instanceof ArmorStand
                || entity instanceof AbstractMinecartContainer || entity instanceof ChestBoat
                || entity instanceof AbstractHorse
                || looterFor(entity) != null;
    }

    public static boolean isTracked(DegradeContext ctx, BlockPos pos) {
        return PlacementTracker.isTracked(ctx.level, pos)
                || MarkedRegions.get(ctx.level).containsBlock(pos);
    }

    public static boolean isPlayerMade(DegradeContext ctx, Entity entity) {
        EntityLooter looter = looterFor(entity);
        if (looter != null) {
            return looter.isPlayerMade(ctx, entity);
        }
        if (entity instanceof ChestBoat || entity instanceof AbstractHorse) {
            return true;
        }
        if (entity instanceof AbstractMinecartContainer) {
            return isTrackedAtOrBelow(ctx, entity);
        }
        return isTracked(ctx, anchorOf(entity));
    }

    public static boolean isTrackedAtOrBelow(DegradeContext ctx, Entity entity) {
        BlockPos at = entity.blockPosition();
        return isTracked(ctx, at) || isTracked(ctx, standingAnchor(at));
    }

    public static BlockPos anchorOf(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return frameAnchor(frame.blockPosition(), frame.getDirection());
        }
        return standingAnchor(entity.blockPosition());
    }

    public static BlockPos frameAnchor(BlockPos framePos, Direction facing) {
        return framePos.relative(facing.getOpposite());
    }

    public static BlockPos standingAnchor(BlockPos entityPos) {
        return entityPos.below();
    }

    private void lootFrame(DegradeContext ctx, ItemFrame frame) {
        ItemStack held = frame.getItem();
        if (!held.isEmpty()) {
            ItemStack result = NestedItems.isContainer(held)
                    ? ItemStack.EMPTY
                    : ContainerLootEffect.keepFractionVisitor(
                            ctx, ctx.chances.containerKeepFraction()).visit(held);
            if (result != held) {
                record(ctx, frame, false);
                frame.setItem(result, false);
                ctx.markChanged();
            }
        }
        if (frame.getItem().isEmpty() && ctx.roll(ctx.chances.doorBreakChance())) {
            record(ctx, frame, true);
            frame.discard();
            ctx.markChanged();
        }
    }

    private void lootArmorStand(DegradeContext ctx, ArmorStand stand) {
        boolean recorded = false;
        float keep = ctx.chances.containerKeepFraction();
        NestedItems.StackVisitor visitor = ContainerLootEffect.keepFractionVisitor(ctx, keep);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = stand.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            NestedItems.Budget budget = ctx.newStackBudget(stand.blockPosition());
            ItemStack working = stack.copy();
            ItemStack result = ctx.nested()
                    ? NestedItems.visitStack(working, visitor, budget)
                    : visitor.visit(working);
            if (result == working && budget.changeCount() == 0) {
                continue;
            }
            if (!recorded) {
                record(ctx, stand, false);
                recorded = true;
            }
            stand.setItemSlot(slot, result);
            ctx.markChanged();
        }
    }

    private void lootChestedAnimal(DegradeContext ctx, AbstractHorse horse) {
        IItemHandler handler = horse.getCapability(Capabilities.ItemHandler.ENTITY, null);
        if (!(handler instanceof IItemHandlerModifiable inventory) || isEmpty(inventory)) {
            return;
        }
        record(ctx, horse, false);
        NestedItems.StackVisitor visitor =
                ContainerLootEffect.keepFractionVisitor(ctx, ctx.chances.containerKeepFraction());
        NestedItems.Budget budget = ctx.newStackBudget(horse.blockPosition());
        boolean changed = ctx.nested()
                ? NestedItems.walk(inventory, visitor, budget)
                : lootTopLevel(inventory, visitor);
        if (changed) {
            ctx.markChanged();
        }
    }

    private void lootContainerEntity(DegradeContext ctx, Entity entity, Container container) {
        if (container.isEmpty()) {
            return;
        }
        record(ctx, entity, false);
        InvWrapper inventory = new InvWrapper(container);
        NestedItems.StackVisitor visitor =
                ContainerLootEffect.keepFractionVisitor(ctx, ctx.chances.containerKeepFraction());
        NestedItems.Budget budget = ctx.newStackBudget(entity.blockPosition());
        boolean changed = ctx.nested()
                ? NestedItems.walk(inventory, visitor, budget)
                : lootTopLevel(inventory, visitor);
        if (changed) {
            ctx.markChanged();
        }
    }

    private static boolean isEmpty(IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean lootTopLevel(IItemHandlerModifiable inventory, NestedItems.StackVisitor visitor) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack result = visitor.visit(stack);
            if (result != stack) {
                inventory.setStackInSlot(slot, result);
                changed = true;
            }
        }
        return changed;
    }

    private static void record(DegradeContext ctx, Entity entity, boolean removed) {
        CompoundTag entry = new CompoundTag();
        entry.put("uuid", NbtUtils.createUUID(entity.getUUID()));
        entry.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        entry.putString("dim", ctx.level.dimension().location().toString());
        entry.put("nbt", entity.saveWithoutId(new CompoundTag()));
        entry.putBoolean("removed", removed);
        CompoundTag section = ctx.undoCompatSection(UNDO_KEY);
        ListTag list = section.getList("entities", Tag.TAG_COMPOUND);
        list.add(entry);
        section.put("entities", list);
    }

    public static void onUndo(MinecraftServer server, CompoundTag section) {
        if (section.isEmpty()) {
            return;
        }
        ListTag entries = section.getList("entities", Tag.TAG_COMPOUND);
        for (int i = entries.size() - 1; i >= 0; i--) {
            CompoundTag entry = entries.getCompound(i);
            restore(server, entry);
        }
    }

    private static void restore(MinecraftServer server, CompoundTag entry) {
        UUID uuid = NbtUtils.loadUUID(entry.get("uuid"));
        CompoundTag nbt = entry.getCompound("nbt");
        for (ServerLevel level : server.getAllLevels()) {
            Entity existing = level.getEntity(uuid);
            if (existing != null) {
                existing.load(nbt);
                return;
            }
        }
        if (!entry.getBoolean("removed")) {
            return;
        }
        ServerLevel level = levelFor(server, entry.getString("dim"));
        if (level == null) {
            return;
        }
        Optional<EntityType<?>> type = EntityType.byString(entry.getString("type"));
        if (type.isEmpty()) {
            return;
        }
        Entity revived = type.get().create(level);
        if (revived == null) {
            return;
        }
        revived.load(nbt);
        revived.setUUID(uuid);
        level.addFreshEntity(revived);
    }

    private static ServerLevel levelFor(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(ResourceLocation.parse(dimension))) {
                return level;
            }
        }
        return null;
    }
}
