package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creative search filters the catalog by display name. Blank query is the
 * selected tab; a query searches every tab so "copper" can find ores that
 * live in Materials from any screen.
 */
class CreativeCatalogSearchTest {

    @Test
    void blankQueryReturnsTheSelectedTab() {
        BlockType[] tab = CreativeCatalog.TABS[0].items();
        BlockType[] shown = CreativeCatalog.itemsFor(0, "");
        assertSame(tab, shown);
        assertArrayEquals(tab, CreativeCatalog.itemsFor(0, "   "));
    }

    @Test
    void copperQueryFindsCopperItemsAcrossTabs() {
        BlockType[] shown = CreativeCatalog.itemsFor(0, "copper");
        assertTrue(shown.length >= 3, "expected ore / crushed / ingot, got " + shown.length);
        Set<BlockType> set = Set.of(shown);
        assertTrue(set.contains(BlockType.COPPER_ORE));
        assertTrue(set.contains(BlockType.CRUSHED_COPPER));
        assertTrue(set.contains(BlockType.COPPER_INGOT));
        for (BlockType t : shown) {
            assertTrue(CreativeCatalog.matches(t, "copper"), t.displayName());
        }
    }

    @Test
    void twoWordsRequireBoth() {
        BlockType[] shown = CreativeCatalog.itemsFor(0, "diamond pickaxe");
        assertEquals(1, shown.length);
        assertEquals(BlockType.DIAMOND_PICKAXE, shown[0]);
        assertFalse(java.util.Arrays.asList(shown).contains(BlockType.DIAMOND_ORE));
    }

    @Test
    void unknownQueryIsEmpty() {
        assertEquals(0, CreativeCatalog.itemsFor(0, "zzzznotanitem").length);
    }

    @Test
    void matchesIsCaseInsensitive() {
        assertTrue(CreativeCatalog.matches(BlockType.COPPER_ORE, "COPPER"));
        assertTrue(CreativeCatalog.matches(BlockType.COPPER_ORE, "Ore"));
        assertFalse(CreativeCatalog.matches(BlockType.STONE, "copper"));
    }

    @Test
    void boneMealIsInTheCatalog() {
        BlockType[] shown = CreativeCatalog.itemsFor(0, "bone meal");
        assertTrue(shown.length >= 1);
        assertTrue(java.util.Arrays.asList(shown).contains(BlockType.BONE_MEAL));
        BlockType[] bones = CreativeCatalog.itemsFor(0, "bones");
        assertTrue(java.util.Arrays.asList(bones).contains(BlockType.BONES));
    }
}
