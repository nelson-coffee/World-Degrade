package dev.ncn.worlddegrade.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDegradeConfigTest {

    @Test
    void qualifiedIdIsKeptVerbatim() {
        assertEquals(Set.of("minecraft:the_nether"),
                WorldDegradeConfig.normalizeDimensions(List.of("minecraft:the_nether")));
    }

    @Test
    void unqualifiedNameGetsMinecraftNamespace() {
        assertEquals(Set.of("minecraft:the_nether"),
                WorldDegradeConfig.normalizeDimensions(List.of("the_nether")));
    }

    @Test
    void moddedNamespaceIsPreserved() {
        assertEquals(Set.of("othermod:some_dimension"),
                WorldDegradeConfig.normalizeDimensions(List.of("othermod:some_dimension")));
    }

    @Test
    void unparseableEntriesAreSkipped() {
        assertTrue(WorldDegradeConfig.normalizeDimensions(List.of("not a valid id")).isEmpty());
    }

    @Test
    void validEntriesSurviveAlongsideInvalidOnes() {
        assertEquals(Set.of("minecraft:the_end"),
                WorldDegradeConfig.normalizeDimensions(List.of("NOT VALID", "the_end")));
    }

    @Test
    void duplicatesCollapseToASingleEntry() {
        assertEquals(Set.of("minecraft:the_nether"),
                WorldDegradeConfig.normalizeDimensions(List.of("the_nether", "minecraft:the_nether")));
    }

    @Test
    void emptyListYieldsEmptySet() {
        assertTrue(WorldDegradeConfig.normalizeDimensions(List.of()).isEmpty());
    }
}
