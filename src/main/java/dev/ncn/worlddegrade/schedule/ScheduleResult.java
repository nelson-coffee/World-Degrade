package dev.ncn.worlddegrade.schedule;

/**
 * Why a {@link ScheduleService#schedule} call did or did not create a schedule. Mirrors
 * {@link dev.ncn.worlddegrade.degrade.DegradeResult}: the reason travels with the result so callers
 * map it to whatever feedback suits them instead of re-running the same validation the service just
 * did to work out which message to show.
 *
 * <p>{@code chunkCount} is the number of chunks the new schedule actually claimed, which is not the
 * number requested: chunks already owned by another schedule are dropped so overlaps cannot
 * double-degrade. Callers should report this rather than their own request size.
 */
public record ScheduleResult(Status status, int id, int chunkCount) {

    public enum Status {
        CREATED,
        /** The feature is off or the configured pass table is empty. */
        DISABLED,
        DIMENSION_DISABLED,
        /** No chunk columns were supplied. */
        EMPTY_AREA,
        /** More chunks than {@link ScheduleService#MAX_CHUNKS} were supplied. */
        TOO_LARGE,
        /** Every supplied chunk already belonged to another schedule. */
        ALREADY_SCHEDULED,
    }

    static ScheduleResult created(int id, int chunkCount) {
        return new ScheduleResult(Status.CREATED, id, chunkCount);
    }

    static ScheduleResult rejected(Status status) {
        return new ScheduleResult(status, -1, 0);
    }

    public boolean created() {
        return status == Status.CREATED;
    }

    /** Translation key describing this outcome, for callers that report to a player or console. */
    public String messageKey() {
        return switch (status) {
            case CREATED -> "chat.worlddegrade.schedule.created";
            case DISABLED -> "chat.worlddegrade.schedule.disabled";
            case DIMENSION_DISABLED -> "chat.worlddegrade.dimension_disabled";
            case TOO_LARGE -> "chat.worlddegrade.schedule.toobig";
            case EMPTY_AREA, ALREADY_SCHEDULED -> "chat.worlddegrade.schedule.nothing";
        };
    }
}
