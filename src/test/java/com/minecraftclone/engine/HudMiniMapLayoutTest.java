package com.minecraftclone.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mini-map lives in HUD logical-square space, then is scaled by 1/aspect
 * at draw time. Its size/offset have to be aspect-aware or a 16:9 window
 * parks a tiny square in the middle of the right half instead of the
 * top-right corner.
 */
class HudMiniMapLayoutTest {

    private static final float EPS = 1e-5f;

    @Test
    void sitsInTheTopRightOfAWidescreenViewport() {
        float aspect = 16f / 9f;
        float sizeX = Hud.miniMapSizeX(aspect);
        float sizeY = Hud.MINI_MAP_SIZE_Y;
        float cx = Hud.miniMapOffsetX(aspect);
        float cy = Hud.miniMapOffsetY();
        float right = cx + sizeX / 2f;
        float top = cy + sizeY / 2f;
        assertEquals(aspect * (1f - Hud.MINI_MAP_MARGIN), right, EPS);
        assertEquals(1f - Hud.MINI_MAP_MARGIN, top, EPS);
        // After the HUD's 1/aspect scale this is NDC x = 1 - margin.
        assertEquals(1f - Hud.MINI_MAP_MARGIN, right / aspect, EPS);
        assertEquals(sizeY, sizeX / aspect, EPS, "on-screen shape must be square");
    }

    @Test
    void isLargerThanTheOldCornerStamp() {
        assertTrue(Hud.MINI_MAP_SIZE_Y >= 0.44f,
                "mini-map should fill ~a quarter of the screen height, not a 0.28 stamp");
    }
}
