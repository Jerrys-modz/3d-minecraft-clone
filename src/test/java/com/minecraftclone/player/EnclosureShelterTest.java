package com.minecraftclone.player;

import com.minecraftclone.world.BlockAccessor;
import com.minecraftclone.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sealed-space cold-shelter rules (see {@link Player#isEnclosedAt} and
 * {@link Player#stepSpaceWarmth}): a house warms up only while fully enclosed
 * - walls on all sides, a roof, solid ground - and a door opening or a wall
 * block breaking opens it back to the elements.
 */
class EnclosureShelterTest {

    /** A tiny fake world: solid dirt by default, with a few override cells. */
    private static final class FakeWorld implements BlockAccessor {
        private final Map<Long, BlockType> cells = new HashMap<>();

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFFL) | (((long) y & 0x1FFFFFL) << 21) | (((long) z & 0x1FFFFFL) << 42);
        }

        FakeWorld set(int x, int y, int z, BlockType t) {
            cells.put(key(x, y, z), t);
            return this;
        }

        @Override
        public BlockType getBlock(int x, int y, int z) {
            BlockType t = cells.get(key(x, y, z));
            return t != null ? t : BlockType.AIR;
        }
    }

    /** A 3x3 sealed room: player at (0,1,0) standing on a floor at y=0, walls at ±1 (y=1,2), roof at y=3. */
    private static FakeWorld sealedRoom() {
        FakeWorld w = new FakeWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.set(dx, 0, dz, BlockType.PLANKS);   // floor
                w.set(dx, 3, dz, BlockType.PLANKS);   // roof
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                w.set(dx, y, -1, BlockType.PLANKS);
                w.set(dx, y, 1, BlockType.PLANKS);
            }
            for (int dz = -1; dz <= 1; dz++) {
                w.set(-1, y, dz, BlockType.PLANKS);
                w.set(1, y, dz, BlockType.PLANKS);
            }
        }
        return w;
    }

    private static final float PX = 0f, PY = 1f, PZ = 0f;

    @Test
    void aSealedRoomIsEnclosed() {
        assertTrue(Player.isEnclosedAt(sealedRoom(), PX, PY, PZ));
    }

    @Test
    void breakingAWallOpensTheSpaceToTheElements() {
        FakeWorld w = sealedRoom();
        // Knock a full 2-high hole in one wall (waist + head) - open to the elements.
        w.set(-1, 1, 0, BlockType.AIR);
        w.set(-1, 2, 0, BlockType.AIR);
        assertFalse(Player.isEnclosedAt(w, PX, PY, PZ));
        // Knock out only the top block of the wall and it's still a wall.
        FakeWorld w2 = sealedRoom();
        w2.set(1, 2, 0, BlockType.AIR);
        assertTrue(Player.isEnclosedAt(w2, PX, PY, PZ), "a 2-high wall with its top block gone still shelters");
    }

    @Test
    void openingTheDoorBreachesTheRoomButClosingItSealsItAgain() {
        FakeWorld w = sealedRoom();
        // A 2-high door in one wall: closed is solid (sealed)...
        w.set(1, 1, 0, BlockType.DOOR);
        w.set(1, 2, 0, BlockType.DOOR);
        assertTrue(Player.isEnclosedAt(w, PX, PY, PZ), "a closed door seals the room");
        // ...open it (walk-through, not solid) and the cold gets in...
        w.set(1, 1, 0, BlockType.DOOR_OPEN);
        w.set(1, 2, 0, BlockType.DOOR_OPEN);
        assertFalse(Player.isEnclosedAt(w, PX, PY, PZ), "an open door is a breach");
        // ...close it again and it's sealed once more.
        w.set(1, 1, 0, BlockType.DOOR);
        w.set(1, 2, 0, BlockType.DOOR);
        assertTrue(Player.isEnclosedAt(w, PX, PY, PZ));
    }

    @Test
    void removingTheRoofOpensTheSpace() {
        FakeWorld w = sealedRoom();
        w.set(0, 3, 0, BlockType.AIR); // a roof block broken out overhead
        assertFalse(Player.isEnclosedAt(w, PX, PY, PZ));
    }

    @Test
    void spaceWarmthRisesWhileSealedAndLeaksWhenBreached() {
        // A cold room heats up over SPACE_WARM_UP_SECONDS (~25s).
        float warm = 0f;
        for (int i = 0; i < 25; i++) warm = Player.stepSpaceWarmth(warm, true, 1f);
        assertEquals(1f, warm, 1e-3f, "a sealed space warms to full");

        // ...and the moment it's breached, it starts cooling (slowly - a house
        // holds its heat for a while after the door opens).
        warm = Player.stepSpaceWarmth(warm, false, 1f);
        assertTrue(warm < 1f, "a breach starts cooling the space");
        assertTrue(warm > 0.9f, "the heat leaks slowly, not instantly");
        for (int i = 0; i < 200; i++) warm = Player.stepSpaceWarmth(warm, false, 1f);
        assertEquals(0f, warm, 1e-3f, "given long enough, it cools all the way");
    }

    @Test
    void aHouseKeepsItsHeatWhileYouAreElsewhere() {
        java.util.Map<Long, Float> heat = new java.util.HashMap<>();
        long house = 1L;
        long outside = 2L;
        // Heat the house to full while sealed...
        float v = 0f;
        for (int i = 0; i < 30; i++) v = Player.updateSpaceHeat(heat, house, true, 1f);
        assertEquals(1f, v, 1e-3f);
        // ...leave (stand in an unsealed cell for a long while)...
        for (int i = 0; i < 300; i++) Player.updateSpaceHeat(heat, outside, false, 1f);
        // ...and the house still holds its warmth - it only cools while you're in it.
        assertEquals(1f, heat.get(house), 1e-3f, "the house holds its temperature while you're away");
        // Standing back inside the (now-breached) house starts cooling it.
        Player.updateSpaceHeat(heat, house, false, 1f);
        assertTrue(heat.get(house) < 1f, "being back in a breached house lets the heat leak");
    }
}
