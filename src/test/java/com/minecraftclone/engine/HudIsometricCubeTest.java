package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Inventory block icons used a squat, bottom-shifted cube that read as a
 * slab sitting in the lower half of the slot. A full cube must be a
 * square centered on the slot; a slab must be half as tall and sit on
 * that same ground line.
 */
class HudIsometricCubeTest {

    private static final float EPS = 1e-4f;
    private static final float HALF = 0.041f;
    private static final float CX = 0.12f;
    private static final float CY = 0.16f;

    @Test
    void fullCubeIsSquareAndCenteredInTheSlot() {
        float[] g = Hud.isometricCube(CX, CY, HALF, 1f);
        float x = g[0], topY = g[1], botFrontY = g[5];
        assertEquals(2f * x, topY - botFrontY, EPS, "full cube bounding box must be square");
        assertEquals(CY, (topY + botFrontY) / 2f, EPS, "full cube must be vertically centred");
        assertTrue(topY <= CY + HALF + EPS, "top must stay inside the slot");
        assertTrue(botFrontY >= CY - HALF - EPS, "bottom must stay inside the slot");
        assertTrue(x > HALF * 0.8f, "cube should fill most of the slot");
    }

    @Test
    void topDiamondIsTwoToOne() {
        float[] g = Hud.isometricCube(CX, CY, HALF, 1f);
        float x = g[0], topY = g[1], frontY = g[3];
        float diamondHeight = topY - frontY;
        float diamondWidth = 2f * x;
        assertEquals(2f, diamondWidth / diamondHeight, EPS, "2:1 dimetric top face");
    }

    @Test
    void slabIsHalfAsTallAndSitsOnTheSameGround() {
        float[] full = Hud.isometricCube(CX, CY, HALF, 1f);
        float[] slab = Hud.isometricCube(CX, CY, HALF, 0.5f);
        float fullBody = full[2] - full[4];
        float slabBody = slab[2] - slab[4];
        assertEquals(fullBody * 0.5f, slabBody, EPS, "slab sides should be half a cube");
        assertEquals(full[5], slab[5], EPS, "slab sits on the full cube's ground line");
        assertTrue(slab[1] < full[1] - EPS, "slab top is below a full cube's top");
    }

    @Test
    void slabAndStairTypesDoNotUseAFullCubeIcon() {
        assertEquals(0.5f, Hud.isometricIconHeight(com.minecraftclone.world.BlockType.STONE_SLAB), EPS);
        assertEquals(0.5f, Hud.isometricIconHeight(com.minecraftclone.world.BlockType.PLANKS_SLAB), EPS);
        assertEquals(1f, Hud.isometricIconHeight(com.minecraftclone.world.BlockType.STONE), EPS);
        assertTrue(com.minecraftclone.world.BlockType.STONE_STAIRS.stair);
        // addIsometricBlock emits a half-height front box (y=0..0.5) and a
        // full-height back box (y=0.5..1). Assert those extents, not a
        // tautological isoPoint == isoPoint comparison.
        float[] slab = Hud.isometricCube(CX, CY, HALF, 0.5f);
        float[] full = Hud.isometricCube(CX, CY, HALF, 1f);
        float frontStepTopY = Hud.isoPoint(CX, CY, HALF, 1f, 0.5f, 1f)[1];
        float backStepTopY  = Hud.isoPoint(CX, CY, HALF, 1f, 1f, 1f)[1];
        assertEquals(slab[1], frontStepTopY, EPS, "front step reaches half-block height");
        assertEquals(full[1], backStepTopY, EPS, "back step reaches full-block height");
        assertTrue(frontStepTopY < backStepTopY - EPS);
    }
}
