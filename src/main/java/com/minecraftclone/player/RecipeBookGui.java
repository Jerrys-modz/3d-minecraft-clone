package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

import java.util.ArrayList;
import java.util.List;

/**
 * State machine for the recipe book screen - a JEI/NEI-style index of every
 * craftable and smeltable item.
 *
 * <p>The book's index is built once per open from two sources: the shaped /
 * shapeless crafting recipes registered in {@link Crafting} (labelled by the
 * grid they need: inventory 2x2, Crafting Table 3x3 or Advanced 5x5) and the
 * furnace recipes in {@link Smelting}. Typing filters the index by output
 * name; selecting an entry opens its detail view (ingredients layout).
 *
 * <p>This class is a <em>pure model</em> - no GL, no world access - so it can
 * be unit-tested directly. Rendering lives in {@code Hud}.
 */
public final class RecipeBookGui {

    /** One row of the book's index. Exactly one of the two sources is non-null. */
    public record Entry(BlockType output, Crafting.Recipe crafting, Smelting.SmeltingRecipe smelting) {

        public boolean isSmelting() {
            return smelting != null;
        }

        /** Human-readable station label for the detail view. */
        public String station() {
            if (isSmelting()) return "Furnace";
            return switch (Crafting.gridSizeOf(crafting)) {
                case 2 -> "Inventory 2x2";
                case 5 -> "Advanced Crafting Table";
                default -> "Crafting Table";
            };
        }

        /** True if the output's item/block name contains the filter text (case-insensitive). */
        public boolean matches(String query) {
            if (query == null || query.isBlank()) return true;
            return output.name().toLowerCase().contains(query.trim().toLowerCase());
        }
    }

    private final List<Entry> index = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private String query = "";
    private int selected = -1;
    private float scroll;

    /**
     * Rebuilds the index from the current registries and clears the search +
     * selection. Called each time the book is opened so newly crafted
     * recipes appear immediately.
     */
    public void rebuildIndex() {
        index.clear();
        for (Crafting.Recipe recipe : Crafting.allRecipes()) {
            index.add(new Entry(recipe.output(), recipe, null));
        }
        for (Smelting.SmeltingRecipe recipe : Smelting.allSmelting()) {
            index.add(new Entry(recipe.output(), null, recipe));
        }
        query = "";
        applyFilter();
        selected = -1;
        scroll = 0f;
    }

    /** The full unfiltered index (registration order: crafting first, then furnace). */
    public List<Entry> index() {
        return index;
    }

    /** The visible (search-filtered) entries, for the grid. */
    public List<Entry> entries() {
        return filtered;
    }

    public String query() {
        return query;
    }

    /** Sets the search filter; selection resets when the filtered list changes. */
    public void setQuery(String text) {
        String next = text == null ? "" : text;
        if (!next.equals(query)) {
            query = next;
            applyFilter();
        }
    }

    /** The currently selected entry, or -1. */
    public int selected() {
        return selected;
    }

    public void select(int index) {
        this.selected = (index >= 0 && index < filtered.size()) ? index : -1;
    }

    /** The selected entry, or null. */
    public Entry selectedEntry() {
        return selected >= 0 && selected < filtered.size() ? filtered.get(selected) : null;
    }

    public float scroll() {
        return scroll;
    }

    public void setScroll(float scroll) {
        this.scroll = Math.max(0f, scroll);
    }

    private void applyFilter() {
        filtered.clear();
        for (Entry e : index) {
            if (e.matches(query)) filtered.add(e);
        }
        if (selected >= filtered.size()) selected = -1;
    }
}
