package com.minecraftclone.world;

import com.minecraftclone.engine.graphics.TextureAtlas;
import com.minecraftclone.player.JoinedStorage;
import com.minecraftclone.player.StorageContainer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Double-chest pairing is pure (block occupancy + a factory), so these
 * tests cover the bugs that used to make doubles "not always form":
 * east-west-only matching, and ignoring a neighbour that was placed
 * but never opened (no entity yet).
 */
class ChestPairingTest {

    @Test
    void isolatedChestHasNoPartner() {
        Chest.Occupied occ = cells(p(0, 0, 0));
        assertNull(Chest.doublePartner(0, 0, 0, occ));
        assertEquals(27, Chest.containerAt(0, 0, 0, occ, newEntities()).size());
    }

    @Test
    void eastWestPairIsMutual() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0));
        assertEquals(new Chest.Partner(1, 0), Chest.doublePartner(0, 0, 0, occ));
        assertEquals(new Chest.Partner(0, 0), Chest.doublePartner(1, 0, 0, occ));
    }

    @Test
    void northSouthPairIsMutual() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(0, 0, 1));
        assertEquals(new Chest.Partner(0, 1), Chest.doublePartner(0, 0, 0, occ));
        assertEquals(new Chest.Partner(0, 0), Chest.doublePartner(0, 0, 1, occ));
    }

    @Test
    void diagonalChestsDoNotPair() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 1));
        assertNull(Chest.doublePartner(0, 0, 0, occ));
        assertNull(Chest.doublePartner(1, 0, 1, occ));
    }

    @Test
    void stackedChestsDoNotPair() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(0, 1, 0));
        assertNull(Chest.doublePartner(0, 0, 0, occ));
        assertNull(Chest.doublePartner(0, 1, 0, occ));
    }

    @Test
    void threeInARowWestPairSticksAndEastStaysSingle() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0), p(2, 0, 0));
        assertEquals(new Chest.Partner(1, 0), Chest.doublePartner(0, 0, 0, occ));
        assertEquals(new Chest.Partner(0, 0), Chest.doublePartner(1, 0, 0, occ),
                "middle of a row must stay with west, not flip to east");
        assertNull(Chest.doublePartner(2, 0, 0, occ), "leftover east chest stays single");
    }

    @Test
    void lShapePairsEastWestNotNorthSouth() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0), p(0, 0, 1));
        assertEquals(new Chest.Partner(1, 0), Chest.doublePartner(0, 0, 0, occ));
        assertEquals(new Chest.Partner(0, 0), Chest.doublePartner(1, 0, 0, occ));
        assertNull(Chest.doublePartner(0, 0, 1, occ), "south leftover of an L stays single");
    }

    @Test
    void openingOneHalfCreatesTheUnopenedNeighbourEntity() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(0, 0, 1));
        Map<String, Chest> ents = new HashMap<>();
        Chest.Factory factory = (x, y, z) -> ents.computeIfAbsent(key(x, y, z), k -> new Chest());
        // Only the clicked chest exists as an entity — the south neighbour
        // is a placed block that was never opened. The old chestAt path
        // treated that as "no double".
        Chest clicked = factory.getOrCreate(0, 0, 0);
        assertEquals(1, ents.size());

        StorageContainer opened = Chest.containerAt(0, 0, 0, occ, factory);
        assertEquals(54, opened.size());
        assertEquals(2, ents.size(), "opening one half must create the neighbour entity");
        assertTrue(opened instanceof JoinedStorage);
        JoinedStorage joined = (JoinedStorage) opened;
        assertSame(clicked, joined.first());
        assertSame(ents.get(key(0, 0, 1)), joined.second());
    }

    @Test
    void eastWestLayoutIsWestFirstWhicheverHalfYouOpen() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0));
        Map<String, Chest> ents = new HashMap<>();
        Chest.Factory factory = (x, y, z) -> ents.computeIfAbsent(key(x, y, z), k -> new Chest());
        JoinedStorage fromWest = (JoinedStorage) Chest.containerAt(0, 0, 0, occ, factory);
        JoinedStorage fromEast = (JoinedStorage) Chest.containerAt(1, 0, 0, occ, factory);
        assertSame(ents.get(key(0, 0, 0)), fromWest.first());
        assertSame(ents.get(key(1, 0, 0)), fromWest.second());
        assertSame(fromWest.first(), fromEast.first());
        assertSame(fromWest.second(), fromEast.second());
    }

    @Test
    void northSouthLayoutIsNorthFirst() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(0, 0, 1));
        Map<String, Chest> ents = new HashMap<>();
        Chest.Factory factory = (x, y, z) -> ents.computeIfAbsent(key(x, y, z), k -> new Chest());
        JoinedStorage fromNorth = (JoinedStorage) Chest.containerAt(0, 0, 0, occ, factory);
        JoinedStorage fromSouth = (JoinedStorage) Chest.containerAt(0, 0, 1, occ, factory);
        assertSame(ents.get(key(0, 0, 0)), fromNorth.first());
        assertSame(ents.get(key(0, 0, 1)), fromNorth.second());
        assertSame(fromNorth.first(), fromSouth.first());
        assertSame(fromNorth.second(), fromSouth.second());
    }

    @Test
    void twoByTwoBecomesAQuadWhicheverCornerYouOpen() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0), p(0, 0, 1), p(1, 0, 1));
        Map<String, Chest> ents = new HashMap<>();
        Chest.Factory factory = (x, y, z) -> ents.computeIfAbsent(key(x, y, z), k -> new Chest());
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            StorageContainer opened = Chest.containerAt(c[0], 0, c[1], occ, factory);
            assertEquals(108, opened.size(), "corner " + c[0] + "," + c[1]);
        }
        assertEquals(4, ents.size());
    }

    @Test
    void eastWestDoubleUsesLeftAndRightHalvesFacingSouth() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0));
        Chest.Appearance west = Chest.appearance(0, 0, 0, occ, null);
        Chest.Appearance east = Chest.appearance(1, 0, 0, occ, null);
        assertEquals(Chest.Kind.DOUBLE, west.kind());
        assertEquals(Chest.Kind.DOUBLE, east.kind());
        assertEquals(0, west.facing(), "east-west pair defaults to facing south");
        assertTrue(west.left());
        assertFalse(east.left());
        assertEquals(TextureAtlas.CHEST_DOUBLE_FRONT_L, Chest.faceTile(west, 0, 0, 1));
        assertEquals(TextureAtlas.CHEST_DOUBLE_FRONT_R, Chest.faceTile(east, 0, 0, 1));
        assertEquals(TextureAtlas.CHEST_DOUBLE_BACK_R, Chest.faceTile(west, 0, 0, -1),
                "from behind, the west half is on the viewer's right");
        assertEquals(TextureAtlas.CHEST_DOUBLE_BACK_L, Chest.faceTile(east, 0, 0, -1));
        assertEquals(TextureAtlas.CHEST_DOUBLE_TOP_L, Chest.faceTile(west, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_DOUBLE_TOP_R, Chest.faceTile(east, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_SIDE_TILE, Chest.faceTile(west, -1, 0, 0));
    }

    @Test
    void northSouthDoubleFacingEastPutsLeftOnTheSouth() {
        BlockAccessor eastFacing = facingAll((byte) 2);
        Chest.Occupied occ = cells(p(0, 0, 0), p(0, 0, 1));
        Chest.Appearance north = Chest.appearance(0, 0, 0, occ, eastFacing);
        Chest.Appearance south = Chest.appearance(0, 0, 1, occ, eastFacing);
        assertEquals(2, north.facing());
        assertTrue(south.left(), "looking west at an east-facing pair, south is on the left");
        assertFalse(north.left());
        assertEquals(TextureAtlas.CHEST_DOUBLE_FRONT_L, Chest.faceTile(south, 1, 0, 0));
        assertEquals(TextureAtlas.CHEST_DOUBLE_FRONT_R, Chest.faceTile(north, 1, 0, 0));
    }

    @Test
    void twoByTwoQuadFacesSouthWithLatchOnSouthRow() {
        Chest.Occupied occ = cells(p(0, 0, 0), p(1, 0, 0), p(0, 0, 1), p(1, 0, 1));
        Chest.Appearance nw = Chest.appearance(0, 0, 0, occ, null);
        Chest.Appearance ne = Chest.appearance(1, 0, 0, occ, null);
        Chest.Appearance sw = Chest.appearance(0, 0, 1, occ, null);
        Chest.Appearance se = Chest.appearance(1, 0, 1, occ, null);
        assertEquals(Chest.Kind.QUAD, nw.kind());
        assertEquals(0, nw.facing());
        assertTrue(nw.left());
        assertFalse(nw.southRow());
        assertTrue(sw.southRow());
        assertTrue(sw.left());
        assertFalse(se.left());
        assertEquals(TextureAtlas.CHEST_DOUBLE_TOP_PLAIN_L, Chest.faceTile(nw, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_DOUBLE_TOP_PLAIN_R, Chest.faceTile(ne, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_DOUBLE_TOP_L, Chest.faceTile(sw, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_DOUBLE_TOP_R, Chest.faceTile(se, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_DOUBLE_FRONT_L, Chest.faceTile(sw, 0, 0, 1));
        assertEquals(TextureAtlas.CHEST_DOUBLE_FRONT_R, Chest.faceTile(se, 0, 0, 1));
        assertEquals(TextureAtlas.CHEST_DOUBLE_BACK_R, Chest.faceTile(nw, 0, 0, -1));
        assertEquals(TextureAtlas.CHEST_DOUBLE_BACK_L, Chest.faceTile(ne, 0, 0, -1));
    }

    @Test
    void isolatedChestKeepsTheSingleTiles() {
        Chest.Occupied occ = cells(p(0, 0, 0));
        Chest.Appearance a = Chest.appearance(0, 0, 0, occ, null);
        assertEquals(Chest.Kind.SINGLE, a.kind());
        assertEquals(TextureAtlas.CHEST_TILE, Chest.faceTile(a, 0, 0, 1));
        assertEquals(TextureAtlas.CHEST_TOP_TILE, Chest.faceTile(a, 0, 1, 0));
        assertEquals(TextureAtlas.CHEST_SIDE_TILE, Chest.faceTile(a, 1, 0, 0));
    }

    @Test
    void lidUvsPutTheLatchOnTheFacingEdge() {
        float[][] south = Chest.lidUvs(0, 0, 1, 1, (byte) 0);
        assertEquals(1f, south[0][1], "south-west vertex sits on the latch edge");
        assertEquals(1f, south[1][1], "south-east vertex sits on the latch edge");
        float[][] east = Chest.lidUvs(0, 0, 1, 1, (byte) 2);
        assertEquals(1f, east[1][1], "south-east vertex is on the east latch");
        assertEquals(1f, east[2][1], "north-east vertex is on the east latch");
    }

    private static BlockAccessor facingAll(byte orientation) {
        return new BlockAccessor() {
            @Override
            public BlockType getBlock(int worldX, int worldY, int worldZ) {
                return BlockType.AIR;
            }

            @Override
            public byte getBlockOrientation(int worldX, int worldY, int worldZ) {
                return orientation;
            }
        };
    }

    private static int[] p(int x, int y, int z) {
        return new int[]{x, y, z};
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static Chest.Occupied cells(int[]... positions) {
        Set<String> set = new HashSet<>();
        for (int[] p : positions) set.add(key(p[0], p[1], p[2]));
        return (x, y, z) -> set.contains(key(x, y, z));
    }

    private static Chest.Factory newEntities() {
        Map<String, Chest> ents = new HashMap<>();
        return (x, y, z) -> ents.computeIfAbsent(key(x, y, z), k -> new Chest());
    }
}
