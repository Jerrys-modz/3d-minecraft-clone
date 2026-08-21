package com.minecraftclone.engine;

import com.minecraftclone.engine.gui.ContainerGui;
import com.minecraftclone.player.Inventory;
import com.minecraftclone.world.tinkers.ToolStationGui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Tool Station used to pack five input slots on the left with the output
 * on the far right, and drew the title on top of the Head/Rod/Extra labels.
 * These tests lock the layout: the station sits above the bag, slots don't
 * overlap, and Head/Rod/Output line up with inventory columns.
 */
class HudToolStationLayoutTest {

    private static final float SLOT = Hud.containerSlotSize();
    private static final float EPS = 1e-4f;

    @Test
    void everyToolStationSlotHasACenter() {
        ContainerGui gui = ContainerGui.forToolStation(new Inventory(), new ToolStationGui());
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = centerOf(gui, id);
            assertNotNull(c, "missing center for slot " + id);
            assertEquals(2, c.length);
        }
    }

    @Test
    void toolStationSlotsDoNotOverlapEachOther() {
        ContainerGui gui = ContainerGui.forToolStation(new Inventory(), new ToolStationGui());
        assertNoOverlap(gui, 0, gui.slotCount());
    }

    @Test
    void toolStationSitsAboveThePlayerInventory() {
        ContainerGui gui = ContainerGui.forToolStation(new Inventory(), new ToolStationGui());
        float bagTop = Float.NEGATIVE_INFINITY;
        for (int id = 0; id < Inventory.SIZE; id++) {
            bagTop = Math.max(bagTop, Hud.playerInventorySlotCenter(id)[1] + SLOT / 2f);
        }
        for (int id = Inventory.SIZE; id < gui.slotCount(); id++) {
            float[] c = Hud.toolStationSlotCenter(id);
            assertNotNull(c, "TS slot " + id);
            float bottom = c[1] - SLOT / 2f;
            assertTrue(bottom > bagTop + EPS,
                    "TS slot " + id + " bottom " + bottom + " overlaps bag top " + bagTop);
        }
    }

    @Test
    void headRodAndOutputAlignToInventoryColumns() {
        assertEquals(Hud.invGridLeftX(), Hud.tsSlotX(0), EPS);
        assertEquals(Hud.invGridLeftX() + 8 * (Hud.tsSlotX(1) - Hud.tsSlotX(0)), Hud.tsOutX(), EPS);
        assertEquals(Hud.tsSlotY(), Hud.tsOutY(), EPS);
        float[] head = Hud.toolStationSlotCenter(ContainerGui.TS_SLOT_0);
        assertEquals(Hud.tsSlotY(), head[1], EPS);
        for (int i = 1; i < ToolStationGui.INPUT_SLOTS; i++) {
            assertTrue(Hud.tsSlotX(i) > Hud.tsSlotX(i - 1));
            float[] c = Hud.toolStationSlotCenter(ContainerGui.TS_SLOT_0 + i);
            assertEquals(Hud.tsSlotY(), c[1], EPS);
        }
    }

    @Test
    void roleLabelsMatchSlotRoles() {
        assertEquals("Head", Hud.tsRoleLabel(0));
        assertEquals("Rod", Hud.tsRoleLabel(1));
        assertEquals("Extra", Hud.tsRoleLabel(2));
        assertEquals("Extra", Hud.tsRoleLabel(4));
    }

    @Test
    void allSlotsStayInsideTheLogicalSquare() {
        ContainerGui gui = ContainerGui.forToolStation(new Inventory(), new ToolStationGui());
        float half = SLOT / 2f;
        for (int id = 0; id < gui.slotCount(); id++) {
            float[] c = centerOf(gui, id);
            assertTrue(c[0] - half >= -1f - EPS && c[0] + half <= 1f + EPS, "x out of range slot " + id);
            assertTrue(c[1] - half >= -1f - EPS && c[1] + half <= 1f + EPS, "y out of range slot " + id);
        }
    }

    private static float[] centerOf(ContainerGui gui, int slotId) {
        float[] ts = Hud.toolStationSlotCenter(slotId);
        if (ts != null) return ts;
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
