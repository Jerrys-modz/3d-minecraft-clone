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

    @Test
    void nanPositionParksTopRightEvenWithACustomSize() {
        float aspect = 16f / 9f;
        Hud.MiniMapLayout layout = Hud.miniMapLayout(aspect, 0.60f, Float.NaN, Float.NaN);
        assertEquals(0.60f, layout.sizeY(), EPS);
        assertEquals(0.60f, layout.sizeX() / aspect, EPS);
        assertEquals(aspect * (1f - Hud.MINI_MAP_MARGIN), layout.maxX(), EPS);
        assertEquals(1f - Hud.MINI_MAP_MARGIN, layout.maxY(), EPS);
    }

    @Test
    void customCentreStaysOnScreenAndSquare() {
        float aspect = 16f / 9f;
        Hud.MiniMapLayout layout = Hud.miniMapLayout(aspect, 0.40f, -0.2f, 0.1f);
        assertEquals(0.40f, layout.sizeY(), EPS);
        assertEquals(0.40f, layout.sizeX() / aspect, EPS);
        assertEquals(-0.2f * aspect, layout.cx(), 1e-4f);
        assertEquals(0.1f, layout.cy(), 1e-4f);
        assertTrue(layout.minX() / aspect >= -1f + Hud.MINI_MAP_MARGIN - EPS);
        assertTrue(layout.maxX() / aspect <= 1f - Hud.MINI_MAP_MARGIN + EPS);
        assertTrue(layout.minY() >= -1f + Hud.MINI_MAP_MARGIN - EPS);
        assertTrue(layout.maxY() <= 1f - Hud.MINI_MAP_MARGIN + EPS);
    }

    @Test
    void hugeCoordinatesClampOntoTheViewport() {
        float aspect = 16f / 9f;
        Hud.MiniMapLayout layout = Hud.miniMapLayout(aspect, 0.48f, 50f, 50f);
        assertEquals(aspect * (1f - Hud.MINI_MAP_MARGIN), layout.maxX(), 1e-4f);
        assertEquals(1f - Hud.MINI_MAP_MARGIN, layout.maxY(), 1e-4f);
        layout = Hud.miniMapLayout(aspect, 0.48f, -50f, -50f);
        assertEquals(aspect * (-1f + Hud.MINI_MAP_MARGIN), layout.minX(), 1e-4f);
        assertEquals(-1f + Hud.MINI_MAP_MARGIN, layout.minY(), 1e-4f);
    }

    @Test
    void hitTestPrefersCornersOverTheBody() {
        float aspect = 16f / 9f;
        Hud.MiniMapLayout layout = Hud.miniMapLayout(aspect, 0.48f, 0f, 0f);
        assertEquals(Hud.MINIMAP_HIT_BODY, Hud.miniMapHit(layout, layout.cx(), layout.cy()));
        assertEquals(Hud.MINIMAP_HIT_TL, Hud.miniMapHit(layout, layout.minX(), layout.maxY()));
        assertEquals(Hud.MINIMAP_HIT_TR, Hud.miniMapHit(layout, layout.maxX(), layout.maxY()));
        assertEquals(Hud.MINIMAP_HIT_BL, Hud.miniMapHit(layout, layout.minX(), layout.minY()));
        assertEquals(Hud.MINIMAP_HIT_BR, Hud.miniMapHit(layout, layout.maxX(), layout.minY()));
        assertEquals(Hud.MINIMAP_HIT_NONE, Hud.miniMapHit(layout, layout.maxX() + 0.3f, layout.cy()));
        assertEquals(Hud.MINIMAP_HIT_NONE, Hud.miniMapHit(null, 0f, 0f));
    }

    @Test
    void resizingACornerKeepsTheOppositeCornerAndTheSquare() {
        float aspect = 16f / 9f;
        Hud.MiniMapLayout start = Hud.miniMapLayout(aspect, 0.48f, 0f, 0f);
        float oppX = start.minX();
        float oppY = start.maxY();
        Hud.MiniMapLayout next = Hud.resizeMiniMap(Hud.MINIMAP_HIT_BR, start,
                start.maxX() + 0.08f * aspect, start.minY() - 0.08f, aspect);
        assertEquals(oppX, next.minX(), 1e-4f);
        assertEquals(oppY, next.maxY(), 1e-4f);
        assertEquals(next.sizeY(), next.sizeX() / aspect, EPS);
        assertTrue(next.sizeY() > start.sizeY());
        assertEquals(start, Hud.resizeMiniMap(Hud.MINIMAP_HIT_BODY, start, 0f, 0f, aspect));
    }

    @Test
    void scaleKeepsTheCentreAndClampsSize() {
        float aspect = 16f / 9f;
        Hud.MiniMapLayout start = Hud.miniMapLayout(aspect, 0.48f, 0.1f, -0.2f);
        Hud.MiniMapLayout bigger = Hud.scaleMiniMap(start, 1.5f, aspect);
        assertEquals(start.cx() / aspect, bigger.cx() / aspect, 1e-4f);
        assertEquals(start.cy(), bigger.cy(), 1e-4f);
        assertTrue(bigger.sizeY() > start.sizeY());
        assertEquals(bigger.sizeY(), bigger.sizeX() / aspect, EPS);

        Hud.MiniMapLayout huge = Hud.scaleMiniMap(start, 100f, aspect);
        assertEquals(Hud.MINI_MAP_SIZE_Y_MAX, huge.sizeY(), EPS);
        Hud.MiniMapLayout tiny = Hud.scaleMiniMap(start, 0.01f, aspect);
        assertEquals(Hud.MINI_MAP_SIZE_Y_MIN, tiny.sizeY(), EPS);
    }
}
