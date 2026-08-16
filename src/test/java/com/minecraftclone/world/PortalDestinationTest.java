package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalDestinationTest {

    @Test
    void netherPortalBouncesOverworldAndNether() {
        assertEquals(DimensionType.NETHER, DimensionType.portalDestination(BlockType.NETHER_PORTAL, DimensionType.OVERWORLD));
        assertEquals(DimensionType.OVERWORLD, DimensionType.portalDestination(BlockType.NETHER_PORTAL, DimensionType.NETHER));
    }

    @Test
    void endPortalBouncesOverworldAndEnd() {
        assertEquals(DimensionType.END, DimensionType.portalDestination(BlockType.END_PORTAL, DimensionType.OVERWORLD));
        assertEquals(DimensionType.OVERWORLD, DimensionType.portalDestination(BlockType.END_PORTAL, DimensionType.END));
    }

    @Test
    void fromTheWrongDimensionAPortalStillLeadsToItsPair() {
        // A nether portal placed in the End leads to the Nether, not nowhere.
        assertEquals(DimensionType.NETHER, DimensionType.portalDestination(BlockType.NETHER_PORTAL, DimensionType.END));
        assertEquals(DimensionType.END, DimensionType.portalDestination(BlockType.END_PORTAL, DimensionType.NETHER));
    }

    @Test
    void nonPortalFallsBackToOverworld() {
        assertEquals(DimensionType.OVERWORLD, DimensionType.portalDestination(BlockType.STONE, DimensionType.OVERWORLD));
    }
}
