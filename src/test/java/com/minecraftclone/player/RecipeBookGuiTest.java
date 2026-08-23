package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JEI-style recipe book's pure model: index building from
 * all machines (crafting grids, furnace, smeltery, Tinkers stations), search
 * filtering, bookmarks (including persistence), and station labels.
 */
class RecipeBookGuiTest {

    private RecipeBookGui book;

    @BeforeEach
    void setUp() {
        book = new RecipeBookGui();
        book.rebuildIndex();
    }

    @Test
    void indexIncludesAllMachineSources() {
        boolean hasCrafting = false, hasFurnace = false, hasSmeltery = false,
                hasPartBuilder = false, hasCasting = false, hasToolStation = false;
        for (RecipeBookGui.Entry e : book.index()) {
            switch (e.station) {
                case "Inventory 2x2", "Crafting Table", "Advanced Crafting Table" -> hasCrafting = true;
                case "Furnace" -> hasFurnace = true;
                case "Smeltery" -> hasSmeltery = true;
                case "Part Builder" -> hasPartBuilder = true;
                case "Casting Table / Basin" -> hasCasting = true;
                case "Tool Station" -> hasToolStation = true;
            }
        }
        assertTrue(hasCrafting, "crafting-grid entries expected");
        assertTrue(hasFurnace, "furnace entries expected");
        assertTrue(hasSmeltery, "smeltery entries expected");
        assertTrue(hasPartBuilder, "part builder entries expected");
        assertTrue(hasCasting, "casting entries expected");
        assertTrue(hasToolStation, "tool station entries expected");
    }

    @Test
    void smelteryOreEntriesNoteDoubleYield() {
        boolean found = false;
        for (RecipeBookGui.Entry e : book.index()) {
            if ("Smeltery".equals(e.station) && e.yieldText.contains("ore x2")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "ore entries at the smeltery should note the x2 yield");
    }

    @Test
    void searchFiltersByTitleAndStation() {
        book.setQuery("smeltery");
        assertFalse(book.entries().isEmpty());
        for (RecipeBookGui.Entry e : book.entries()) {
            String hay = (e.title + " " + e.station).toLowerCase();
            assertTrue(hay.contains("smeltery"));
        }
        book.setQuery("zzzznotanitem");
        assertTrue(book.entries().isEmpty());
        assertEquals(-1, book.selected());
    }

    @Test
    void selectAndClearSelection() {
        assertFalse(book.entries().isEmpty());
        book.select(0);
        assertEquals(0, book.selected());
        assertEquals(book.entries().get(0).title, book.selectedEntry().title);

        book.select(-1);
        assertEquals(-1, book.selected());
    }

    @Test
    void rebuildIndexResetsQuerySelectionKeepsBookmarks() {
        int before = book.index().size();
        book.setQuery("iron");
        assertFalse(book.entries().isEmpty());

        var idsBefore = book.bookmarkIds();
        book.rebuildIndex(book.bookmarkIds());
        assertEquals(before, book.index().size());
        assertEquals("", book.query());
        assertEquals(idsBefore.size(), book.bookmarkIds().size());
    }

    @Test
    void bookmarkToggleSortsEntryFirst() {
        RecipeBookGui.Entry last = book.index().get(book.index().size() - 1);
        String id = last.bookmarkId;
        assertTrue(book.toggleBookmarkById(id));

        book.rebuildIndex(book.bookmarkIds()); // simulate persist + rebuild cycle
        assertTrue(book.index().get(0).bookmarked, "bookmarked entries should sort first");
        assertEquals(id, book.index().get(0).bookmarkId);

        // Un-bookmark via the toggle path on a fresh build: the entry starts
        // bookmarked, so toggling removes it.
        book.rebuildIndex(book.bookmarkIds());
        assertTrue(book.isBookmarked(book.index().stream()
                .filter(e -> e.bookmarkId.equals(id)).findFirst().orElse(null)));
        assertFalse(book.toggleBookmarkById(id));
        assertFalse(book.isBookmarked(book.index().stream()
                .filter(e -> e.bookmarkId.equals(id)).findFirst().orElse(null)));
    }

    @Test
    void bookmarksPersistToFile(@TempDir Path dir) {
        Path file = dir.resolve("recipe_bookmarks.txt");
        book.rebuildIndex(book.bookmarkIds());
        RecipeBookGui.Entry target = book.index().get(5);
        assertTrue(book.toggleBookmarkById(target.bookmarkId));
        book.saveBookmarks(file);

        RecipeBookGui other = new RecipeBookGui();
        other.loadBookmarks(file);
        other.rebuildIndex(other.bookmarkIds());
        assertEquals(1, other.bookmarkIds().size());
        assertTrue(other.isBookmarked(other.index().get(0)));
    }

    @Test
    void toolStationEntriesUseRegisteredMaterials() {
        boolean any = false;
        for (RecipeBookGui.Entry e : book.index()) {
            if ("Tool Station".equals(e.station)) {
                any = true;
                // Tool Station recipes list head+rod as part ingredients.
                assertEquals(2, e.ingredients.size());
                break;
            }
        }
        assertTrue(any, "tool station entries expected");
    }
}
