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
}
