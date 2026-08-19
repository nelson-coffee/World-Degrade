package dev.ncn.worlddegrade.schedule;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Per-dimension persistent store of active degradation schedules (#5). It holds only what cannot be
 * recomputed: which chunks belong to which schedule, when the schedule was triggered, and which pass
 * is next. Nothing about individual blocks is stored — the per-chunk tracked-block attachment (#1)
 * already knows what is left to degrade, and a pass simply degrades whatever still exists.
 *
 * <p>The store is keyed the "wrong" way round on purpose: a {@code chunk -> entryId} reverse map is
 * the payload, with a light per-entry header on the side. That makes the two hottest operations O(1)
 * on a per-block-placement path — "is this chunk scheduled?" and "release the schedule covering this
 * chunk" — and makes overlapping schedules impossible by construction, since a chunk key can only
 * point at one entry.
 *
 * <p>The pass <em>table</em> (delays/levels) is intentionally not stored per entry; it is read from
 * the live config so an admin editing it applies to in-flight schedules. Only {@code nextPass} is
 * persisted; {@code load} floors it at 0, and {@link #pollDue} drops any entry whose {@code nextPass}
 * has fallen off the end of the current (possibly shortened) table.
 */
public class ScheduledDegradations extends SavedData {

    private static final String NAME = "worlddegrade_schedules";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A single scheduled area. Mutable header; the chunk set is fixed once created. */
    public static final class Entry {
        private final int id;
        private long triggerGameTime;
        private int nextPass;
        private int uses;
        private final ScheduleSource source;
        private final LongOpenHashSet chunks;

        Entry(int id, long triggerGameTime, int nextPass, int uses, ScheduleSource source,
              LongOpenHashSet chunks) {
            this.id = id;
            this.triggerGameTime = triggerGameTime;
            this.nextPass = nextPass;
            this.uses = uses;
            this.source = source;
            this.chunks = chunks;
        }

        public int id() {
            return id;
        }

        public ScheduleSource source() {
            return source;
        }

        public int nextPass() {
            return nextPass;
        }

        public int uses() {
            return uses;
        }

        public long triggerGameTime() {
            return triggerGameTime;
        }

        public int chunkCount() {
            return chunks.size();
        }

        LongOpenHashSet chunks() {
            return chunks;
        }
    }

    private final Long2IntOpenHashMap chunkToEntry = new Long2IntOpenHashMap();
    private final Int2ObjectOpenHashMap<Entry> entries = new Int2ObjectOpenHashMap<>();
    private int nextId = 1;
    private long lastCheckGameTime = -1L;

    public ScheduledDegradations() {
        chunkToEntry.defaultReturnValue(-1);
    }

    public static ScheduledDegradations get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /**
     * The store for this dimension, or null when it has none. Unlike {@link #get} this never creates
     * one, so the per-second scheduler check does not materialise a saved-data file for every
     * dimension on the server the moment the feature is switched on.
     */
    @Nullable
    public static ScheduledDegradations existing(ServerLevel level) {
        return level.getDataStorage().get(factory(), NAME);
    }

    private static SavedData.Factory<ScheduledDegradations> factory() {
        return new SavedData.Factory<>(ScheduledDegradations::new, ScheduledDegradations::load, null);
    }

    /**
     * Registers a new schedule over the given chunks, dropping any already owned by another schedule
     * so overlaps never double-degrade. Returns the new id, or {@code -1} when nothing remained to
     * schedule.
     */
    public int schedule(LongOpenHashSet requested, long triggerGameTime) {
        return schedule(requested, triggerGameTime, ScheduleSource.GLOBAL);
    }

    public int schedule(LongOpenHashSet requested, long triggerGameTime, ScheduleSource source) {
        LongOpenHashSet owned = new LongOpenHashSet(requested.size());
        for (long chunk : requested) {
            if (!chunkToEntry.containsKey(chunk)) {
                owned.add(chunk);
            }
        }
        if (owned.isEmpty()) {
            return -1;
        }
        int id = nextId++;
        entries.put(id, new Entry(id, triggerGameTime, 0, 0, source, owned));
        for (long chunk : owned) {
            chunkToEntry.put(chunk, id);
        }
        setDirty();
        return id;
    }

    /**
     * Records one unit of "something is building here" against whichever schedule owns the chunk.
     * When the recorded uses reach {@code threshold} the whole schedule is cancelled, marking the area
     * as inhabited again. A {@code threshold} of 0 turns the check off: nothing is counted and no
     * schedule is ever cancelled this way. Returns {@code true} only when a schedule was cancelled.
     */
    public boolean markInUse(long chunk, int threshold) {
        return markInUse(chunk, source -> threshold);
    }

    /**
     * As {@link #markInUse(long, int)} but with the threshold resolved from the owning entry's
     * {@link ScheduleSource}, so OPAC schedules (#6) can use a different {@code releaseBlockThreshold}
     * from manual ones.
     */
    public boolean markInUse(long chunk, ToIntFunction<ScheduleSource> thresholdBySource) {
        int id = chunkToEntry.get(chunk);
        if (id < 0) {
            return false;
        }
        Entry entry = entries.get(id);
        if (entry == null) {
            return false;
        }
        int threshold = thresholdBySource.applyAsInt(entry.source);
        if (threshold <= 0) {
            return false;
        }
        entry.uses++;
        if (entry.uses >= threshold) {
            removeEntry(id);
            return true;
        }
        setDirty();
        return false;
    }

    public boolean isScheduled(long chunk) {
        return chunkToEntry.get(chunk) >= 0;
    }

    /** Force-cancel a schedule by id, ignoring any usage threshold. */
    public boolean removeById(int id) {
        if (!entries.containsKey(id)) {
            return false;
        }
        removeEntry(id);
        return true;
    }

    public int clear() {
        int removed = entries.size();
        entries.clear();
        chunkToEntry.clear();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Read-only view; mutating it would desync the {@code chunk -> entryId} reverse map. */
    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Nullable
    public Entry entry(int id) {
        return entries.get(id);
    }

    /**
     * Absorbs time during which the scheduler was not running, keeping schedule clocks frozen while the
     * feature is switched off. Called once per scheduler check with the interval between checks: any
     * gap beyond that interval is time nobody was counting, so every trigger is pushed forward by it
     * and each entry keeps the delay it had left.
     *
     * <p>Without this, {@code getGameTime()} keeps advancing while {@code schedule.enabled = false} and
     * a schedule left paused for a week would find every pass overdue the moment it is re-enabled.
     * Server downtime needs no special handling — game time does not advance while the server is off.
     *
     * <p>The reference point is dirtied every time it moves. A stale one is not harmless: the store is
     * otherwise only written when a schedule is created or fires, so a quiet dimension would persist
     * the reference point from creation time and discount every hour of uptime since it as paused on
     * the next restart — passes longer than the interval between restarts would never come due.
     *
     * @return the number of paused ticks absorbed, for logging
     */
    public long absorbPause(long now, int checkIntervalTicks) {
        long previous = lastCheckGameTime;
        // With nothing scheduled there is no clock to preserve, so the reference point is dropped
        // rather than tracked: the next schedule then starts from a fresh one instead of inheriting a
        // gap, and an idle dimension stops dirtying the store once per check.
        long updated = entries.isEmpty() ? -1L : now;
        if (updated != previous) {
            lastCheckGameTime = updated;
            setDirty();
        }
        if (previous < 0 || now <= previous || entries.isEmpty()) {
            return 0L;
        }
        long paused = now - previous - checkIntervalTicks;
        if (paused <= 0) {
            return 0L;
        }
        for (Entry entry : entries.values()) {
            entry.triggerGameTime += paused;
        }
        setDirty();
        return paused;
    }

    /**
     * Returns the <em>most overdue</em> entry at {@code now}, or null when none is due. Picking the
     * longest-waiting one rather than the first in hash order matters because only one pass starts per
     * check: with several entries due at once, hash order could let the same entry win repeatedly while
     * another waits arbitrarily long.
     *
     * <p>Entries are sanitised as they are scanned: a trigger time in the future (restored backup,
     * hand-edited save) is clamped to now, and an entry whose {@code nextPass} has fallen off the end
     * of the current table is removed.
     */
    @Nullable
    public Entry pollDue(long now, DegradeSchedule schedule) {
        return pollDue(now, source -> schedule);
    }

    /**
     * Source-aware overload: each entry's table is resolved from its {@link ScheduleSource}, so OPAC
     * entries (#6) can come due on their own table independently of the global one. An entry whose
     * resolved table is <em>empty</em> is skipped, not pruned — an empty table means "misconfigured or
     * paused right now", the same state the whole feature is in when switched off, and the entry must
     * survive until the table is restored.
     */
    @Nullable
    public Entry pollDue(long now, Function<ScheduleSource, DegradeSchedule> tableBySource) {
        Entry mostOverdue = null;
        long earliestDue = Long.MAX_VALUE;
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (entry.triggerGameTime > now) {
                LOGGER.warn("World Degrade: schedule #{} has a trigger time in the future ({} > {}); "
                        + "clamping to now", entry.id, entry.triggerGameTime, now);
                entry.triggerGameTime = now;
                setDirty();
            }
            DegradeSchedule schedule = tableBySource.apply(entry.source);
            if (schedule.isEmpty()) {
                continue;
            }
            if (entry.nextPass >= schedule.passes().size()) {
                removeEntry(entry.id);
                continue;
            }
            long dueTime = entry.triggerGameTime + schedule.passes().get(entry.nextPass).delayTicks();
            if (now >= dueTime && dueTime < earliestDue) {
                earliestDue = dueTime;
                mostOverdue = entry;
            }
        }
        return mostOverdue;
    }

    /** Advances an entry one pass forward, removing it once its passes are exhausted. */
    public void advance(Entry entry, DegradeSchedule schedule) {
        advanceTo(entry, entry.nextPass, source -> schedule);
    }

    public void advanceTo(Entry entry, int firedPass, DegradeSchedule schedule) {
        advanceTo(entry, firedPass, source -> schedule);
    }

    /**
     * Advances an entry to the pass after {@code firedPass}, so the scheduler can skip intermediate
     * overdue passes in one step. Resets the lived-in {@code uses} counter — the threshold measures
     * activity <em>since the last pass</em>, not cumulatively over the whole schedule's lifetime.
     * Removes the entry once its passes are exhausted, using the entry's own resolved table.
     */
    public void advanceTo(Entry entry, int firedPass, Function<ScheduleSource, DegradeSchedule> tableBySource) {
        entry.nextPass = firedPass + 1;
        entry.uses = 0;
        if (entry.nextPass >= tableBySource.apply(entry.source).passes().size()) {
            removeEntry(entry.id);
        } else {
            setDirty();
        }
    }

    private void removeEntry(int id) {
        Entry entry = entries.remove(id);
        if (entry == null) {
            return;
        }
        for (long chunk : entry.chunks) {
            chunkToEntry.remove(chunk);
        }
        setDirty();
    }

    static ScheduledDegradations load(CompoundTag tag, HolderLookup.Provider registries) {
        ScheduledDegradations data = new ScheduledDegradations();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        int maxId = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            long[] chunkArray = entryTag.getLongArray("chunks");
            if (chunkArray.length == 0) {
                continue;
            }
            int id = entryTag.getInt("id");
            // A non-positive id would collide with the chunkToEntry sentinel (-1) and a duplicate id
            // would orphan the shadowed entry's chunks in the reverse map forever, so both are dropped.
            if (id <= 0 || data.entries.containsKey(id)) {
                LOGGER.warn("World Degrade: skipping schedule entry with invalid or duplicate id {}", id);
                continue;
            }
            LongOpenHashSet chunks = new LongOpenHashSet(chunkArray.length);
            for (long chunk : chunkArray) {
                chunks.add(chunk);
            }
            // Missing on saves written before #6, and any unknown key from a newer build, load as
            // GLOBAL — the pre-#6 behaviour.
            ScheduleSource source = ScheduleSource.fromKey(entryTag.getString("source"));
            Entry entry = new Entry(id, entryTag.getLong("trigger"),
                    Math.max(0, entryTag.getInt("pass")), Math.max(0, entryTag.getInt("uses")),
                    source, chunks);
            data.entries.put(id, entry);
            for (long chunk : chunks) {
                data.chunkToEntry.put(chunk, id);
            }
            maxId = Math.max(maxId, id);
        }
        data.nextId = Math.max(tag.getInt("nextId"), maxId + 1);
        // Absent on stores written before pause tracking existed; -1 simply means "no reference point
        // yet", so the first check after load establishes one without shifting anything.
        data.lastCheckGameTime = tag.contains("lastCheck") ? tag.getLong("lastCheck") : -1L;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("id", entry.id);
            entryTag.putLong("trigger", entry.triggerGameTime);
            entryTag.putInt("pass", entry.nextPass);
            entryTag.putInt("uses", entry.uses);
            entryTag.putString("source", entry.source.key());
            // Sorted, spatially-clustered chunk longs compress far better than hash order.
            long[] sorted = entry.chunks.toLongArray();
            Arrays.sort(sorted);
            entryTag.putLongArray("chunks", sorted);
            list.add(entryTag);
        }
        tag.put("entries", list);
        tag.putInt("nextId", nextId);
        tag.putLong("lastCheck", lastCheckGameTime);
        return tag;
    }
}
