package com.minecraftclone.world;

import com.minecraftclone.player.Crafting;
import com.minecraftclone.world.gen.WorldGenSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The Steel Drum, and tanks/drums giving their fluid back as cells. */
class SteelDrumTest {

    @TempDir
    Path dir;

    @Test
    void drumHoldsHalfATank() {
        FluidTankEntity drum = new FluidTankEntity(true);
        assertEquals(FluidTankEntity.DRUM_CAPACITY_MB, drum.capacity());
        assertEquals(FluidTankEntity.DRUM_CAPACITY_MB, drum.fill(Fluid.WATER, 100_000));
        assertEquals(BlockType.STEEL_DRUM, drum.blockType());
        assertEquals(FluidTankEntity.DRUM_TYPE, drum.type());
        assertEquals(FluidTankEntity.CAPACITY_MB, new FluidTankEntity().capacity());
    }

    @Test
    void drumSavesAndLoadsAsADrum() throws Exception {
        FluidTankEntity drum = new FluidTankEntity(true);
        drum.fill(Fluid.DIESEL, 9000);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        drum.writeTo(new DataOutputStream(buf));
        BlockEntity loaded = BlockEntities.create(FluidTankEntity.DRUM_TYPE);
        loaded.readFrom(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
        FluidTankEntity t = (FluidTankEntity) loaded;
        assertEquals(BlockType.STEEL_DRUM, t.blockType());
        assertEquals(9000, t.amount());
        assertEquals(Fluid.DIESEL, t.fluid());
    }

    @Test
    void placedDrumIsFoundByMachines() {
        World w = new World(42, new WorldGenSettings(), null, dir.resolve("w"), DimensionType.OVERWORLD, true);
        w.ensureChunk(0, 0);
        w.setBlock(2, 100, 2, BlockType.STEEL_DRUM);
        assertInstanceOf(FluidTankEntity.class, w.blockEntityAt(2, 100, 2));
        FluidTankEntity drum = w.getOrCreateSteelTank(2, 100, 2);
        assertEquals(FluidTankEntity.DRUM_CAPACITY_MB, drum.capacity());
        drum.fill(Fluid.WATER, 1000);
        assertSame(drum, FluidNetwork.findTankWith(w, 2, 100, 3, Fluid.WATER, 100));
    }

    @Test
    void brokenContainersGiveBackWholeCells() {
        FluidTankEntity t = new FluidTankEntity();
        assertEquals(0, t.wholeCells());
        t.fill(Fluid.LAVA, 4700);
        assertEquals(4, t.wholeCells(), "4700 mB -> 4 lava cells, the 700 mB remainder is lost");
    }

    @Test
    void recipe() {
        BlockType Q = BlockType.STEEL_INGOT, P = BlockType.STEEL_PLATE;
        assertEquals(BlockType.STEEL_DRUM, Crafting.match3x3(new BlockType[]{Q, P, Q, Q, null, Q, Q, P, Q}).output());
    }
}
