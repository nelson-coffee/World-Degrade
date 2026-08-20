package dev.ncn.worlddegrade.schedule;

/**
 * What created a schedule, so each entry can resolve its own pass table (#6). The persistence key is
 * stable and independent of the enum's ordinal: entries save the key, and a save written before this
 * field existed — or with a key from a newer build — loads back as {@link #GLOBAL}, the pre-#6
 * behaviour.
 *
 * <p>The table itself is still not stored per entry (see {@link ScheduledDegradations}); only this
 * source is, and the live config maps it to a table. That keeps admin edits reaching in-flight
 * schedules while letting OPAC-triggered runs use a different table from manual ones.
 */
public enum ScheduleSource {
    /** Manual {@code /degrade schedule} runs and any API caller that does not name a source. */
    GLOBAL("global"),
    /** Open Parties and Claims claim expirations (#6). */
    OPAC("opac");

    private final String key;

    ScheduleSource(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Maps a persisted key back to a source, defaulting to {@link #GLOBAL} for missing/unknown keys. */
    public static ScheduleSource fromKey(String key) {
        for (ScheduleSource source : values()) {
            if (source.key.equals(key)) {
                return source;
            }
        }
        return GLOBAL;
    }
}
