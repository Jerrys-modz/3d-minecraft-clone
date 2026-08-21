package com.minecraftclone.engine;

import com.minecraftclone.player.CreativeCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Materials tab grew large enough to run through the hotbar. The catalog
 * is now a clipped viewport with a row-scroll offset: only in-view slots are
 * hittable, and scrolling by one row parks the next row on the top line.
 */
class HudCreativeCatalogLayoutTest {

    private static final float EPS = 1e-4f;

    @Test
    void materialsTabOverflowsTheVisibleViewport() {
        int materials = materialsItemCount();
        assertTrue(Hud.catalogRowCount(materials) > Hud.catalogVisibleRows(),
                "Materials should need scrolling after the GTNH ore dump");
        assertTrue(Hud.catalogMaxScroll(materials) > 0f);
    }

    @Test
    void scrollingByOneRowParksTheNextRowOnTop() {
        float[] row0 = Hud.catalogItemCenter(0, 0f);
        float[] row1At0 = Hud.catalogItemCenter(9, 0f);
        float[] row1Scrolled = Hud.catalogItemCenter(9, 1f);
        assertEquals(row0[1], row1Scrolled[1], EPS);
        assertTrue(row1At0[1] < row0[1]);
        assertEquals(row0[0], Hud.catalogItemCenter(9, 0f)[0], EPS);
    }

    @Test
    void itemsBelowTheHotbarAreNotVisibleOrHittable() {
        int materials = materialsItemCount();
        int last = materials - 1;
        assertFalse(Hud.catalogItemVisible(last, 0f),
                "the last Materials item must start off-screen");
        float[] off = Hud.catalogItemCenter(last, 0f);
        assertTrue(off[1] < Hud.catalogClipBottomY());
        assertEquals(-1, Hud.catalogItemAt(off[0], off[1], materials, 0f));

        float scrolled = Hud.catalogMaxScroll(materials);
        assertTrue(Hud.catalogItemVisible(last, scrolled));
        float[] on = Hud.catalogItemCenter(last, scrolled);
        assertEquals(last, Hud.catalogItemAt(on[0], on[1], materials, scrolled));
    }

    @Test
    void clampKeepsScrollInRange() {
        int n = materialsItemCount();
        assertEquals(0f, Hud.clampCatalogScroll(-3f, n));
        assertEquals(Hud.catalogMaxScroll(n), Hud.clampCatalogScroll(999f, n));
        assertEquals(0f, Hud.clampCatalogScroll(1f, 8), "a short tab has nothing to scroll");
    }

    @Test
    void visibleItemsSitAboveTheHotbar() {
        int vis = Hud.catalogVisibleRows();
        for (int i = 0; i < vis * 9; i++) {
            float y = Hud.catalogItemCenter(i, 0f)[1];
            assertTrue(y >= Hud.catalogClipBottomY() - EPS,
                    "row item " + i + " y=" + y + " overlaps the hotbar");
        }
    }

    @Test
    void searchBoxSitsBetweenTabsAndTheGrid() {
        float searchTop = Hud.searchBoxTop();
        float searchBot = Hud.searchBoxBottom();
        float tabBot = 0.80f - 0.07f / 2f;
        float gridTop = Hud.catalogItemCenter(0, 0f)[1] + 0.09f / 2f;
        assertTrue(searchBot > gridTop + EPS, "search overlaps the catalog grid");
        assertTrue(searchTop < tabBot - EPS, "search overlaps the tab strip");
        assertTrue(Hud.searchBoxLeft() < 0f && Hud.searchBoxRight() > 0f);
    }

    private static int materialsTabIndex() {
        for (int i = 0; i < CreativeCatalog.TABS.length; i++) {
            if ("Materials".equals(CreativeCatalog.TABS[i].label())) return i;
        }
        fail("Materials tab missing");
        return -1;
    }

    private static int materialsItemCount() {
        return CreativeCatalog.TABS[materialsTabIndex()].items().length;
    }
}
