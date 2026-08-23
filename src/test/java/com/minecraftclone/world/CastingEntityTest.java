package com.minecraftclone.world;

import com.minecraftclone.player.ItemStack;
import com.minecraftclone.world.tinkers.TinkersItem;
import com.minecraftclone.world.tinkers.ToolPartType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Casting Table / Basin brain: cast imprinting, material
 * feeding, powered batch production, output caps and save round-trips.
 * Powered state is forced via the package-private test hook so no real
 * smeltery structure is needed.
 */
class CastingEntityTest {

    private static ItemStack part(ToolPartType shape) {
        return ItemStack.tinkersPart(new TinkersItem.Part(shape, BlockType.PLANKS));
    }

    private static void tickFor(CastingEntity e, float seconds) {
        for (float t = 0; t < seconds; t += 1f) e.tick(1f);
    }

    @Test
    void imprintRequiresATinkersPart() {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        assertFalse(table.imprintCast(ItemStack.EMPTY));
        assertFalse(table.imprintCast(ItemStack.of(BlockType.IRON_INGOT, 1)));
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        assertEquals(ToolPartType.PICK_HEAD, table.castShape());
    }

    @Test
    void feedingRequiresACastAndARegisteredMaterial() {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        assertEquals(0, table.insertMaterial(BlockType.IRON_INGOT, 4)); // no cast yet
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        assertEquals(4, table.insertMaterial(BlockType.IRON_INGOT, 4));
        assertEquals(0, table.insertMaterial(BlockType.DIRT, 2)); // not a material
    }

    @Test
    void tableCastsOnePartPerCycle() {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        table.setPoweredForTesting(true);
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        table.insertMaterial(BlockType.IRON_INGOT, 3);

        tickFor(table, CastingEntity.CAST_SECONDS);
        assertEquals(1, table.outputCount()); // batch of 1 on a table
        assertEquals(2, table.inputCount());

        tickFor(table, CastingEntity.CAST_SECONDS * 2f);
        assertEquals(3, table.outputCount());
        assertEquals(0, table.inputCount());
    }

    @Test
    void basinCastsInBatchesOfThree() {
        CastingEntity basin = new CastingEntity(BlockType.CASTING_BASIN, true);
        basin.setPoweredForTesting(true);
        assertTrue(basin.imprintCast(part(ToolPartType.PICK_HEAD)));
        basin.insertMaterial(BlockType.IRON_INGOT, 7);

        tickFor(basin, CastingEntity.CAST_SECONDS);
        assertEquals(3, basin.outputCount()); // batch of 3 on a basin
        assertEquals(4, basin.inputCount());
    }

    @Test
    void productionPausesWithoutPower() {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        table.insertMaterial(BlockType.IRON_INGOT, 2);
        tickFor(table, CastingEntity.CAST_SECONDS); // no smeltery nearby
        assertEquals(0, table.outputCount());
    }

    @Test
    void outputCapStopsProduction() {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        table.setPoweredForTesting(true);
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        table.insertMaterial(BlockType.IRON_INGOT, 16);
        // Table holds at most 4 finished parts: 4 consumed, 12 left queued.
        tickFor(table, CastingEntity.CAST_SECONDS * 10f);
        assertEquals(4, table.outputCount());
        assertEquals(12, table.inputCount());
        assertEquals(4, table.takeOutputs().size());
    }

    @Test
    void basinBatchIsCappedByRemainingOutputSpace() {
        CastingEntity basin = new CastingEntity(BlockType.CASTING_BASIN, true);
        basin.setPoweredForTesting(true);
        assertTrue(basin.imprintCast(part(ToolPartType.LARGE_PLATE)));
        basin.insertMaterial(BlockType.IRON_INGOT, 18);

        tickFor(basin, CastingEntity.CAST_SECONDS * 5f);
        assertEquals(15, basin.outputCount());
        assertEquals(3, basin.inputCount());
        tickFor(basin, CastingEntity.CAST_SECONDS);
        assertEquals(CastingEntity.BASIN_OUTPUT_CAP, basin.outputCount());
        assertEquals(2, basin.inputCount());
    }

    @Test
    void partialOutputCollectionLeavesUnacceptedPartsStored() {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        table.setPoweredForTesting(true);
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        table.insertMaterial(BlockType.IRON_INGOT, 4);
        tickFor(table, CastingEntity.CAST_SECONDS * 4f);

        assertEquals(2, table.takeOutputs(2).size());
        assertEquals(2, table.outputCount());
    }

    @Test
    void persistsCastInputsAndOutputs() throws Exception {
        CastingEntity table = new CastingEntity(BlockType.CASTING_TABLE, false);
        table.setPoweredForTesting(true);
        assertTrue(table.imprintCast(part(ToolPartType.PICK_HEAD)));
        table.insertMaterial(BlockType.IRON_INGOT, 5);
        tickFor(table, CastingEntity.CAST_SECONDS);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        table.writeTo(new DataOutputStream(buf));
        CastingEntity loaded = new CastingEntity(BlockType.CASTING_TABLE, false);
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));

        assertEquals(ToolPartType.PICK_HEAD, loaded.castShape());
        assertEquals(BlockType.IRON_INGOT, loaded.inputType());
        assertEquals(4, loaded.inputCount());
        assertEquals(1, loaded.outputCount());
    }

    @Test
    void deserializationRejectsNonMaterialInputAndCapsValidInput() throws Exception {
        CastingEntity invalid = readState(BlockType.DIRT, 100, 0);
        assertNull(invalid.inputType());
        assertEquals(0, invalid.inputCount());

        CastingEntity capped = readState(BlockType.IRON_INGOT, 100, 0);
        assertEquals(BlockType.IRON_INGOT, capped.inputType());
        assertEquals(CastingEntity.TABLE_INPUT_CAP, capped.inputCount());
    }

    @Test
    void deserializationRejectsOutputCountAboveCapacity() throws Exception {
        CastingEntity loaded = readState(BlockType.IRON_INGOT, 1,
                CastingEntity.TABLE_OUTPUT_CAP + 1);
        assertNull(loaded.castShape());
        assertEquals(0, loaded.inputCount());
        assertEquals(0, loaded.outputCount());
    }

    private static CastingEntity readState(BlockType input, int inputCount, int outputCount) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(1);
        out.writeBoolean(true);
        out.writeUTF(ToolPartType.PICK_HEAD.name());
        out.writeBoolean(true);
        out.writeShort(input.id);
        out.writeInt(inputCount);
        out.writeInt(outputCount);
        for (int i = 0; i < outputCount; i++) {
            out.writeUTF(ToolPartType.PICK_HEAD.name());
            out.writeShort(BlockType.IRON_INGOT.id);
        }
        out.writeFloat(0f);
        CastingEntity loaded = new CastingEntity(BlockType.CASTING_TABLE, false);
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        return loaded;
    }
}
