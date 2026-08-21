package com.minecraftclone.engine;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.world.tinkers.PartBuilderGui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Part Builder used to stack a 2×4 shape grid on top of the player
 * inventory (and paint an arrow through the buttons). These tests lock the
 * layout contract: every slot has a unique hit box, and the station sits
 * strictly above the bag.
 */
class HudPartBuilderLayoutTest {

    private static final float SLOT = Hud.containerSlotSize();
    private static final float EPS = 1e-4f;

    @Test
    void everyPartBuilderSlotHasACenter() {
        ContainerGui gui = ContainerGui.forPartBuilder(new Inventory(), new PartBuilderGui());
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = centerOf(gui, id);
            assertNotNull(c, "missing center for slot " + id);
            assertEquals(2, c.length);
        }
    }

    @Test
    void partBuilderSlotsDoNotOverlapEachOther() {
        ContainerGui gui = ContainerGui.forPartBuilder(new Inventory(), new PartBuilderGui());
        assertNoOverlap(gui, 0, gui.slotCount());
    }

    @Test
    void partBuilderStationSitsAboveThePlayerInventory() {
        ContainerGui gui = ContainerGui.forPartBuilder(new Inventory(), new PartBuilderGui());
        float bagTop = Float.NEGATIVE_INFINITY;
        for (int id = 0; id < Inventory.SIZE; id++) {
            bagTop = Math.max(bagTop, Hud.playerInventorySlotCenter(id)[1] + SLOT / 2f);
        }
        for (int id = Inventory.SIZE; id < gui.slotCount(); id++) {
            float[] c = Hud.partBuilderSlotCenter(id);
            assertNotNull(c, "PB slot " + id);
            float bottom = c[1] - SLOT / 2f;
            assertTrue(bottom > bagTop + EPS,
                    "PB slot " + id + " bottom " + bottom + " overlaps bag top " + bagTop);
        }
    }

    @Test
    void shapeButtonsAreAFourByTwoGridAboveTheBag() {
        assertEquals(8, ContainerGui.PB_SHAPE_COUNT);
        float y0 = Hud.pbShapeY(0);
        float y4 = Hud.pbShapeY(4);
        assertTrue(y0 > y4, "row 0 should be above row 1");
        for (int i = 0; i < 4; i++) {
            assertEquals(y0, Hud.pbShapeY(i), EPS);
            assertEquals(y4, Hud.pbShapeY(i + 4), EPS);
            if (i > 0) {
                assertTrue(Hud.pbShapeX(i) > Hud.pbShapeX(i - 1));
            }
        }
    }

    @Test
    void materialAndOutputSitOnTheInventoryColumnsNotOnTheShapeGrid() {
        // Material shares the bag's left column, output the right; both are
        // vertically centred on the 2-row shape grid, so they must not share
        // an x with any shape button.
        float matX = Hud.pbMatX();
        float outX = Hud.pbOutX();
        assertEquals(Hud.invGridLeftX(), matX, EPS);
        for (int i = 0; i < ContainerGui.PB_SHAPE_COUNT; i++) {
            float sx = Hud.pbShapeX(i);
            assertTrue(Math.abs(sx - matX) >= SLOT,
                    "shape " + i + " overlaps material horizontally");
            assertTrue(Math.abs(sx - outX) >= SLOT,
                    "shape " + i + " overlaps output horizontally");
        }
    }

    @Test
    void allSlotsStayInsideTheLogicalSquare() {
        ContainerGui gui = ContainerGui.forPartBuilder(new Inventory(), new PartBuilderGui());
        float half = SLOT / 2f;
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = centerOf(gui, id);
            assertTrue(c[0] - half >= -1f - EPS && c[0] + half <= 1f + EPS, "x out of range slot " + id);
            assertTrue(c[1] - half >= -1f - EPS && c[1] + half <= 1f + EPS, "y out of range slot " + id);
        }
    }

    private static float[] centerOf(ContainerGui gui, int slotId) {
        float[] pb = Hud.partBuilderSlotCenter(slotId);
        if (pb != null) return pb;
        if (gui.isPlayerSlot(slotId)) return Hud.playerInventorySlotCenter(slotId);
        return null;
    }

    private static void assertNoOverlap(ContainerGui gui, int from, int to) {
        for (int i = from; i < to; i++) {
            float[] a = centerOf(gui, i);
            for (int j = i + 1; j < to; j++) {
                float[] b = centerOf(gui, j);
                boolean overlap = Math.abs(a[0] - b[0]) < SLOT - EPS
                        && Math.abs(a[1] - b[1]) < SLOT - EPS;
                assertFalse(overlap, "slots " + i + " and " + j
                        + " overlap at (" + a[0] + "," + a[1] + ") / ("
                        + b[0] + "," + b[1] + ")");
            }
        }
    }
}
