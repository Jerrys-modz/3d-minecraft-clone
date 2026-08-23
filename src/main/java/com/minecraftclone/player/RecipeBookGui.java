package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import com.minecraftclone.world.Mining;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.TinkersRegistry;
import com.minecraftclone.world.tinkers.ToolPartType;
import com.minecraftclone.world.Mining;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * State machine for the recipe book screen - a JEI/NEI-style index of every
 * craftable and smeltable item across ALL machines.
 *
 * <p>The index is rebuilt on open from four sources:
 * <ul>
 *   <li>Crafting grids - inventory 2x2, Crafting Table 3x3, Advanced 5x5
 *       ({@link Crafting}).</li>
 *   <li>Furnace smelting ({@link Smelting}).</li>
 *   <li>The Smeltery - the same smeltables, with the ore double-yield bonus.</li>
 *   <li>Tinkers stations - Part Builder / Casting Table / Casting Basin part
 *       casting for every registered material and shape, plus Tool Station
 *       assembly of every tool kind.</li>
 * </ul>
 *
 * <p>Typing filters by name; clicking selects an entry for the detail card;
 * pressing B toggles a bookmark on the selection. Bookmarked entries sort
 * first and are persisted by the caller.
 *
 * <p>Pure model - no GL or world access - so it is unit-testable directly.
 */
public final class RecipeBookGui {

    /** One row of the book's index. */
    public static final class Entry {
        public final ItemStack preview;   // what the grid/detail icons draw
        public final String title;        // display name
        public final String station;      // machine that produces it
        public final List<ItemStack> ingredients; // detail-card ingredient icons
        public final int gridCols;        // >0: lay ingredients out as a grid with this many columns; 0: flat row
        public final String yieldText;    // e.g. "-> 4x Stone Stairs"
        public final String bookmarkId;   // stable id used for persistence
        public boolean bookmarked;

        Entry(ItemStack preview, String title, String station,
              List<ItemStack> ingredients, int gridCols, String yieldText, String bookmarkId) {
            this.preview = preview;
            this.title = title;
            this.station = station;
            this.ingredients = ingredients;
            this.gridCols = gridCols;
            this.yieldText = yieldText;
            this.bookmarkId = bookmarkId;
        }

        /** True if the searchable text contains the filter (case-insensitive). */
        public boolean matches(String query) {
            if (query == null || query.isBlank()) return true;
            String q = query.trim().toLowerCase();
            return (title + " " + station).toLowerCase().contains(q);
        }
    }

    private final List<Entry> index = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final Set<String> bookmarks = new HashSet<>();
    private String query = "";
    private int selected = -1;
    private float scroll;

    /** Loads persisted bookmark ids (one per line); missing file = empty set. */
    public void loadBookmarks(java.nio.file.Path file) {
        bookmarks.clear();
        if (!java.nio.file.Files.isRegularFile(file)) return;
        try {
            for (String line : java.nio.file.Files.readAllLines(file)) {
                String id = line.trim();
                if (!id.isEmpty()) bookmarks.add(id);
            }
        } catch (IOException ignored) {
        }
    }

    /** Saves bookmark ids (one per line) to {@code file}. */
    public void saveBookmarks(java.nio.file.Path file) {
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file, bookmarks.stream().sorted().toList(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /**
     * Rebuilds the index from the current registries and clears query +
     * selection. Called each time the book opens so new content appears.
     */
    public void rebuildIndex() {
        rebuildIndex(bookmarks);
    }

    /** As {@link #rebuildIndex()}, keeping the given set of bookmarked ids. */
    public void rebuildIndex(Set<String> bookmarkIds) {
        bookmarks.clear();
        if (bookmarkIds != null) bookmarks.addAll(bookmarkIds);
        index.clear();
        buildCraftingEntries();
        buildFurnaceEntries();
        buildSmelteryEntries();
        buildPartBuilderAndCastingEntries();
        buildToolStationEntries();
        // Apply persisted bookmark flags so entries render + sort correctly.
        for (Entry e : index) {
            e.bookmarked = bookmarks.contains(e.bookmarkId);
        }
        sortBookmarksFirst();
        query = "";
        applyFilter();
        selected = -1;
        scroll = 0f;
    }

    public List<Entry> index() { return index; }

    public List<Entry> entries() { return filtered; }

    public String query() { return query; }

    /** Sets the search filter; the selection resets whenever the filter changes. */
    public void setQuery(String text) {
        String next = text == null ? "" : text;
        if (!next.equals(query)) {
            query = next;
            applyFilter();
        }
    }

    public int selected() { return selected; }

    public void select(int index) {
        this.selected = (index >= 0 && index < filtered.size()) ? index : -1;
    }

    /** The selected entry, or null. */
    public Entry selectedEntry() {
        return selected >= 0 && selected < filtered.size() ? filtered.get(selected) : null;
    }

    public float scroll() { return scroll; }

    public void setScroll(float scroll) { this.scroll = Math.max(0f, scroll); }

    /** True if the given entry's id is bookmarked. */
    public boolean isBookmarked(Entry e) {
        return e != null && bookmarks.contains(e.bookmarkId);
    }

    /** All bookmarked ids (for persistence). */
    public Set<String> bookmarkIds() {
        return new HashSet<>(bookmarks);
    }

    /**
     * Toggles the bookmark on the selected entry. Returns true if it is now
     * bookmarked, false if un-bookmarked or nothing is selected.
     */
    public boolean toggleBookmarkOnSelection() {
        Entry e = selectedEntry();
        if (e == null) return false;
        boolean added = toggleBookmarkById(e.bookmarkId);
        e.bookmarked = added;
        sortBookmarksFirst();
        applyFilter();
        // Keep pointing at the same entry in the re-sorted list.
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).bookmarkId.equals(e.bookmarkId)) {
                selected = i;
                break;
            }
        }
        return added;
    }

    /** Toggles a bookmark by id; returns true if the id is now bookmarked. */
    public boolean toggleBookmarkById(String id) {
        if (id == null || id.isEmpty()) return false;
        if (bookmarks.remove(id)) {
            return false;
        }
        bookmarks.add(id);
        return true;
    }

    // -----------------------------------------------------------------------
    // Index builders
    // -----------------------------------------------------------------------

    private void buildCraftingEntries() {
        for (Crafting.Recipe r : Crafting.allRecipes()) {
            int gridSize = Crafting.gridSizeOf(r);
            String station = switch (gridSize) {
                case 2 -> "Inventory 2x2";
                case 5 -> "Advanced Crafting Table";
                default -> "Crafting Table";
            };
            List<ItemStack> ing = new ArrayList<>();
            int cols = 0;
            String yieldText;
            if (r instanceof Crafting.ShapedRecipe shaped) {
                for (BlockType b : shaped.pattern()) {
                    ing.add(b == null ? ItemStack.EMPTY : ItemStack.of(b, 1));
                }
                cols = gridSize;
                yieldText = "-> " + shaped.outputAmount() + "x " + shaped.output().displayName();
            } else if (r instanceof Crafting.ShapelessRecipe shapeless) {
                var grouped = new java.util.LinkedHashMap<BlockType, Integer>();
                for (BlockType b : shapeless.ingredients()) grouped.merge(b, 1, Integer::sum);
                for (var e : grouped.entrySet()) {
                    ing.add(ItemStack.of(e.getKey(), e.getValue()));
                }
                yieldText = "-> " + shapeless.outputAmount() + "x " + shapeless.output().displayName();
            } else {
                continue;
            }
            String id = "craft:" + r.output().name() + ":" + gridSize + ":"
                    + Integer.toHexString(java.util.Arrays.hashCode(patternKey(r)));
            index.add(new Entry(ItemStack.of(r.output(), r.outputAmount()),
                    r.output().displayName(), station, ing, cols, yieldText, id));
        }
    }

    private static int[] patternKey(Crafting.Recipe r) {
        if (r instanceof Crafting.ShapedRecipe s) {
            int[] ids = new int[s.pattern().length];
            for (int i = 0; i < ids.length; i++) ids[i] = s.pattern()[i] == null ? 0 : s.pattern()[i].id;
            return ids;
        }
        if (r instanceof Crafting.ShapelessRecipe s) {
            int[] ids = new int[s.ingredients().length];
            for (int i = 0; i < ids.length; i++) ids[i] = s.ingredients()[i].id;
            return ids;
        }
        return new int[0];
    }

    private void buildFurnaceEntries() {
        for (Smelting.SmeltingRecipe s : Smelting.allSmelting()) {
            List<ItemStack> ing = new ArrayList<>();
            ing.add(ItemStack.of(s.input(), 1));
            String id = "furnace:" + s.input().name() + "->" + s.output().name();
            index.add(new Entry(ItemStack.of(s.output(), 1), s.output().displayName(),
                    "Furnace", ing, 0, "Smelt 1x " + s.input().displayName(), id));
        }
    }

    private void buildSmelteryEntries() {
        for (Smelting.SmeltingRecipe s : Smelting.allSmelting()) {
            int yield = com.minecraftclone.world.multiblock.SmelteryEntity.yieldFor(s.input());
            List<ItemStack> ing = new ArrayList<>();
            ing.add(ItemStack.of(s.input(), 1));
            String bonus = yield > 1 ? " (ore x" + yield + ")" : "";
            String id = "smeltery:" + s.input().name() + "->" + s.output().name();
            index.add(new Entry(ItemStack.of(s.output(), yield), s.output().displayName(),
                    "Smeltery", ing, 0, "Melt 1x " + s.input().displayName() + bonus, id));
        }
    }

    private void buildPartBuilderAndCastingEntries() {
        for (BlockType material : TinkersRegistry.materials()) {
            for (ToolPartType shape : ToolPartType.values()) {
                ItemStack preview = ItemStack.tinkersPart(new TinkersItem.Part(shape, material));
                String title = shape.name() + " (" + material.displayName() + ")";
                List<ItemStack> ing = new ArrayList<>();
                ing.add(ItemStack.of(material, 1));

                String pbId = "pb:" + shape.name() + ":" + material.name();
                index.add(new Entry(preview, title, "Part Builder", new ArrayList<>(ing), 0,
                        "-> 1x " + shape.name(), pbId));

                String castId = "cast:" + shape.name() + ":" + material.name();
                index.add(new Entry(preview, title, "Casting Table / Basin", new ArrayList<>(ing), 0,
                        "-> 1x " + shape.name() + " (cast)", castId));
            }
        }
    }

    private void buildToolStationEntries() {
        for (BlockType material : TinkersRegistry.materials()) {
            for (ToolPartType head : ToolPartType.values()) {
                if (head.assembledKind == null) continue;
                Mining.ToolKind kind = head.assembledKind;
                List<ItemStack> parts = new ArrayList<>();
                parts.add(ItemStack.tinkersPart(new TinkersItem.Part(head, material)));
                parts.add(ItemStack.tinkersPart(new TinkersItem.Part(ToolPartType.TOOL_ROD, material)));

                ItemStack preview = ItemStack.tinkersTool(
                        new TinkersItem.Tool(kind, List.of(new TinkersItem.ToolLayer(head, material))));
                String title = kind.name() + " (" + material.displayName() + ")";
                String id = "ts:" + kind.name() + ":" + material.name();
                index.add(new Entry(preview, title, "Tool Station", parts, 0,
                        "Assemble " + head.name() + " + rod", id));
            }
        }
    }

    /** Bookmarked entries float to the top (stable within their group). */
    private void sortBookmarksFirst() {
        index.sort((a, b) -> Boolean.compare(b.bookmarked, a.bookmarked));
    }

    private void applyFilter() {
        filtered.clear();
        for (Entry e : index) {
            if (e.matches(query)) filtered.add(e);
        }
        if (selected >= filtered.size()) selected = -1;
    }
}
