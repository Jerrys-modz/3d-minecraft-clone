package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JEI-style recipe book's pure model: index building from
 * the crafting + furnace registries, search filtering, selection, and the
 * station labels shown in the detail view.
 */
class RecipeBookGuiTest {

    private RecipeBookGui book;

    @BeforeEach
    void setUp() {
        book = new RecipeBookGui();
        book.rebuildIndex();
    }

    @Test
    void indexIncludesCraftingAndFurnaceRecipes() {
        assertFalse(book.index().isEmpty());

        // Crafting: iron ore -> iron ingot is a 3x3 smelting-table recipe? No -
        // IRON_ORE -> INGOT lives in Smelting; check crafting via planks.
        boolean hasPlanksRecipe = false;
        boolean hasSmelting = false;
        for (RecipeBookGui.Entry e : book.index()) {
            if (e.output() == BlockType.PLANKS && !e.isSmelting()) hasPlanksRecipe = true;
            if (e.output() == BlockType.IRON_INGOT && e.isSmelting()) hasSmelting = true;
        }
        assertTrue(hasPlanksRecipe, "planks (log -> 4 planks) should be indexed as crafting");
        assertTrue(hasSmelting, "iron ore should be indexed under furnace recipes");
    }

    @Test
    void searchFiltersByOutputName() {
        book.setQuery("planks");
        assertFalse(book.entries().isEmpty());
        for (RecipeBookGui.Entry e : book.entries()) {
            assertTrue(e.output().name().toLowerCase().contains("planks"));
        }
        // No match at all.
        book.setQuery("zzzznotanitem");
        assertTrue(book.entries().isEmpty());
        assertNull(book.selectedEntry());
    }

    @Test
    void selectAndClearSelection() {
        assertFalse(book.entries().isEmpty());
        book.select(0);
        assertEquals(0, book.selected());
        assertEquals(book.entries().get(0).output(), book.selectedEntry().output());

        book.select(-1); // out of range clears
        assertEquals(-1, book.selected());
        assertNull(book.selectedEntry());
    }

    @Test
    void stationLabelsMatchSource() {
        int smelting = 0, crafting = 0;
        for (RecipeBookGui.Entry e : book.index()) {
            if (e.isSmelting()) {
                assertEquals("Furnace", e.station());
                smelting++;
            } else {
                assertTrue(e.station().equals("Inventory 2x2")
                        || e.station().equals("Crafting Table")
                        || e.station().equals("Advanced Crafting Table"));
                crafting++;
            }
        }
        assertTrue(smelting > 0, "smelting entries expected");
        assertTrue(crafting > 0, "crafting entries expected");
    }

    @Test
    void rebuildIndexRefreshesAndResets() {
        int before = book.index().size();
        book.setQuery("iron");
        assertFalse(book.entries().isEmpty());
        book.select(0);

        book.rebuildIndex(); // re-opening the book
        assertEquals(before, book.index().size());
        assertEquals("", book.query());
        assertEquals(-1, book.selected()); // selection cleared
        assertFalse(book.entries().isEmpty());
    }

    @Test
    void scrollClampsToNonNegative() {
        book.setScroll(-5f);
        assertEquals(0f, book.scroll(), 0.001f);
        book.setScroll(1000f); // clamped against real content in Hud; model allows any positive
        assertTrue(book.scroll() >= 0f);
    }
}
