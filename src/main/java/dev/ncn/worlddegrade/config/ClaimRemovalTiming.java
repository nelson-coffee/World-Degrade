package dev.ncn.worlddegrade.config;

/**
 * When an OPAC-triggered schedule (#6) drops the expired claim so other players can loot the ruin.
 * Deliberately free of any OPAC type so {@link ServerConfig} can reference it directly — the actual
 * unclaim call lives in the OPAC compat, which is the only place that touches OPAC classes.
 */
public enum ClaimRemovalTiming {
    /** After the last degradation pass has finished — the area is a full ruin before it opens up. */
    FINAL_PASS,
    /** After the first pass has finished — the area looks ruined, then keeps crumbling while looted. */
    FIRST_PASS,
    /** As soon as the schedule is created, before any degradation. */
    SCHEDULE,
    /** Never — the expired claim is left in place. */
    NEVER
}
