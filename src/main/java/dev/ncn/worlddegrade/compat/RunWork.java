package dev.ncn.worlddegrade.compat;

public interface RunWork {
    boolean tick();

    int changedBlocks();

    /**
     * How many compat targets (contraptions, trains, ships) this unit will process, so a run whose
     * only work is a contraption does not acknowledge itself as "0 chunks" and read like a no-op.
     */
    default int targetCount() {
        return 0;
    }
}
