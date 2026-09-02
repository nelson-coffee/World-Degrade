package dev.ncn.worlddegrade.degrade;

import dev.ncn.worlddegrade.config.WorldDegradeConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EnderChestLootQueue extends SavedData {
    private static final String NAME = "worlddegrade_enderchest_loot";
    private static final long MILLIS_PER_DAY = 86_400_000L;

    public record Event(long id, float keepFraction, long queuedAt, Set<UUID> applied) {
    }

    private final LongOpenHashSet lootedChests = new LongOpenHashSet();
    private final List<Event> events = new ArrayList<>();
    private long nextId = 1L;

    public static EnderChestLootQueue get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EnderChestLootQueue::new, EnderChestLootQueue::load, null), NAME);
    }

    private static EnderChestLootQueue load(CompoundTag tag, HolderLookup.Provider registries) {
        EnderChestLootQueue queue = new EnderChestLootQueue();
        for (long packed : tag.getLongArray("lootedChests")) {
            queue.lootedChests.add(packed);
        }
        queue.nextId = tag.getLong("nextId");
        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Set<UUID> applied = new HashSet<>();
            ListTag appliedTag = entry.getList("applied", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < appliedTag.size(); j++) {
                applied.add(NbtUtils.loadUUID(appliedTag.get(j)));
            }
            queue.events.add(new Event(entry.getLong("id"), entry.getFloat("keep"),
                    entry.getLong("queuedAt"), applied));
        }
        return queue;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("lootedChests", lootedChests.toLongArray());
        tag.putLong("nextId", nextId);
        ListTag list = new ListTag();
        for (Event event : events) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", event.id());
            entry.putFloat("keep", event.keepFraction());
            entry.putLong("queuedAt", event.queuedAt());
            ListTag applied = new ListTag();
            for (UUID player : event.applied()) {
                applied.add(NbtUtils.createUUID(player));
            }
            entry.put("applied", applied);
            list.add(entry);
        }
        tag.put("events", list);
        return tag;
    }

    @Nullable
    public Event beginLoot(long chestPos, float keepFraction) {
        if (!lootedChests.add(chestPos)) {
            return null;
        }
        Event event = new Event(nextId++, keepFraction, System.currentTimeMillis(), new HashSet<>());
        events.add(event);
        setDirty();
        return event;
    }

    public void markApplied(Event event, UUID player) {
        if (event.applied().add(player)) {
            setDirty();
        }
    }

    public void cancel(long eventId) {
        if (events.removeIf(event -> event.id() == eventId)) {
            setDirty();
        }
    }

    public List<Event> pendingFor(UUID player) {
        expire();
        List<Event> owed = new ArrayList<>();
        for (Event event : events) {
            if (!event.applied().contains(player)) {
                owed.add(event);
            }
        }
        return owed;
    }

    private void expire() {
        long cutoff = System.currentTimeMillis() - WorldDegradeConfig.enderLootExpiryDays() * MILLIS_PER_DAY;
        Iterator<Event> iterator = events.iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            if (iterator.next().queuedAt() < cutoff) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed) {
            setDirty();
        }
    }

    public void forgetChest(long chestPos) {
        if (lootedChests.remove(chestPos)) {
            setDirty();
        }
    }

    public static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        return List.copyOf(server.getPlayerList().getPlayers());
    }
}
