package dev.ncn.worlddegrade.degrade;

import org.jetbrains.annotations.Nullable;

/**
 * Why a {@link DegradeService#start} call did or did not begin a run.
 *
 * <p>A bare {@code null} conflated three outcomes with completely different follow-ups, which left
 * every caller re-deriving the reason by repeating the same validation the service had just done.
 * The schedule (#5) loops on this value and only {@link Status#BUSY} is worth retrying: an empty or
 * unproductive area will keep being empty, and a disabled dimension never becomes enabled on its own.
 */
public record DegradeResult(Status status, @Nullable DegradeJob job) {

    public enum Status {
        STARTED,
        /** Another degradation or undo is in flight. The only status worth retrying. */
        BUSY,
        DIMENSION_DISABLED,
        /** The area selects no chunk columns at all, so there is nothing to scan. */
        EMPTY_AREA,
        /** The area is real but holds no tracked blocks and no compat work. */
        NOTHING_FOUND,
    }

    static DegradeResult started(DegradeJob job) {
        return new DegradeResult(Status.STARTED, job);
    }

    static DegradeResult rejected(Status status) {
        return new DegradeResult(status, null);
    }

    public boolean started() {
        return status == Status.STARTED;
    }

    /** Whether the same call could succeed later without anything else changing. */
    public boolean worthRetrying() {
        return status == Status.BUSY;
    }

    /** Translation key describing this outcome, for callers that report to a player or console. */
    public String messageKey() {
        return switch (status) {
            case STARTED -> "chat.worlddegrade.area.started";
            case BUSY -> "chat.worlddegrade.busy";
            case DIMENSION_DISABLED -> "chat.worlddegrade.dimension_disabled";
            case EMPTY_AREA, NOTHING_FOUND -> "chat.worlddegrade.nothing";
        };
    }
}
