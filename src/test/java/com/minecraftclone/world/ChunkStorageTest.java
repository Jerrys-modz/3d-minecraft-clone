package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkStorageTest {

    /** A GL-free stand-in for {@link Chunk} (whose Mesh construction needs an OpenGL context). */
    private static final class StubChunk implements ChunkStorage.PersistableChunk {
        private final ChunkPos pos;
        private final byte[] blocks = new byte[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        private final byte[] overlays = new byte[blocks.length];

        StubChunk(ChunkPos pos) {
            this.pos = pos;
        }

        @Override
        public ChunkPos getPos() {
            return pos;
        }

        @Override
        public byte[] getRawBlocks() {
            return blocks;
        }

        @Override
        public void setRawBlocks(byte[] data) {
            System.arraycopy(data, 0, blocks, 0, Math.min(data.length, blocks.length));
        }

        @Override
        public byte[] getRawOverlays() {
            return overlays;
        }

        @Override
        public void setRawOverlays(byte[] data) {
            System.arraycopy(data, 0, overlays, 0, Math.min(data.length, overlays.length));
        }

        @Override
        public void markGenerated() {
        }
    }

    /** A second, non-furnace block entity to prove the system handles multiple machine types. */
    static final class TestMachine implements BlockEntity {
        static final String TYPE = "test_machine";
        private String label = "";
        private int count;

        TestMachine() {
        }

        TestMachine(String label, int count) {
            this.label = label;
            this.count = count;
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public BlockType blockType() {
            return BlockType.STONE;
        }

        @Override
        public void writeTo(DataOutput out) throws IOException {
            out.writeUTF(label);
            out.writeInt(count);
        }

        @Override
        public void readFrom(DataInput in) throws IOException {
            label = in.readUTF();
            count = in.readInt();
        }

        String label() {
            return label;
        }

        int count() {
            return count;
        }
    }

    /** A block entity whose type was deliberately "removed" - must be skipped on load, not fatal. */
    private static final class RemovedMachine implements BlockEntity {
        @Override
        public String type() {
            return "removed_machine";
        }

        @Override
        public BlockType blockType() {
            return BlockType.GLASS;
        }

        @Override
        public void writeTo(DataOutput out) throws IOException {
            out.writeInt(12345); // arbitrary payload of a type the game no longer knows
            out.writeBoolean(true);
        }

        @Override
        public void readFrom(DataInput in) throws IOException {
            in.readInt();
            in.readBoolean();
        }
    }

    static {
        BlockEntities.register(TestMachine.TYPE, TestMachine::new);
    }

    @Test
    void savesAndRestoresBlocksOverlaysAndBlockEntities() throws IOException {
        Path dir = Files.createTempDirectory("mcclone-save-test");
        try {
            ChunkStorage storage = new ChunkStorage(dir);
            ChunkPos pos = new ChunkPos(3, -2);

            StubChunk a = new StubChunk(pos);
            a.getRawBlocks()[0] = BlockType.FURNACE.id;
            a.getRawBlocks()[10] = BlockType.STONE.id;
            a.getRawOverlays()[5] = BlockType.SEAWEED.id;

            Furnace f = new Furnace();
            f.setSlot(Furnace.SLOT_INPUT, BlockType.GOLD_ORE, 4);
            f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL, 2);
            f.tick(3f);

            storage.save(a, List.of(new ChunkStorage.BlockEntitySave(48, 40, -25, f)));

            StubChunk b = new StubChunk(pos);
            List<ChunkStorage.BlockEntitySave> restored = storage.load(b);

            assertEquals(BlockType.FURNACE.id, b.getRawBlocks()[0]);
            assertEquals(BlockType.STONE.id, b.getRawBlocks()[10]);
            assertEquals(BlockType.SEAWEED.id, b.getRawOverlays()[5]);
            assertEquals(1, restored.size());
            ChunkStorage.BlockEntitySave es = restored.get(0);
            assertEquals(48, es.x());
            assertEquals(40, es.y());
            assertEquals(-25, es.z());
            assertTrue(es.entity() instanceof Furnace);
            Furnace g = (Furnace) es.entity();
            assertEquals(BlockType.GOLD_ORE, g.typeOf(Furnace.SLOT_INPUT));
            assertEquals(4, g.countOf(Furnace.SLOT_INPUT));
            assertEquals(BlockType.COAL, g.typeOf(Furnace.SLOT_FUEL));
            assertEquals(1, g.countOf(Furnace.SLOT_FUEL), "one of two coals burned during tick(3)");
            assertEquals(f.progressFraction(), g.progressFraction(), 0.001f);
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void multipleEntityTypesRoundTripInOneChunk() throws IOException {
        Path dir = Files.createTempDirectory("mcclone-save-test");
        try {
            ChunkStorage storage = new ChunkStorage(dir);
            ChunkPos pos = new ChunkPos(0, 0);

            Furnace f = new Furnace();
            f.setSlot(Furnace.SLOT_OUTPUT, BlockType.DIAMOND, 2);
            TestMachine m = new TestMachine("compressor", 7);

            storage.save(new StubChunk(pos),
                    List.of(new ChunkStorage.BlockEntitySave(1, 2, 3, f),
                            new ChunkStorage.BlockEntitySave(4, 5, 6, m)));

            List<ChunkStorage.BlockEntitySave> restored = storage.load(new StubChunk(pos));
            assertEquals(2, restored.size());
            assertEquals("furnace", restored.get(0).entity().type());
            assertEquals(1, restored.get(0).x());
            assertEquals(2, restored.get(0).y());
            assertEquals(3, restored.get(0).z());
            assertEquals(BlockType.DIAMOND, ((Furnace) restored.get(0).entity()).typeOf(Furnace.SLOT_OUTPUT));
            assertEquals("test_machine", restored.get(1).entity().type());
            TestMachine rm = (TestMachine) restored.get(1).entity();
            assertEquals("compressor", rm.label());
            assertEquals(7, rm.count());
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void unknownEntityTypeIsSkippedWithoutBreakingTheFile() throws IOException {
        Path dir = Files.createTempDirectory("mcclone-save-test");
        try {
            ChunkStorage storage = new ChunkStorage(dir);
            ChunkPos pos = new ChunkPos(0, 0);

            Furnace f = new Furnace();
            f.setSlot(Furnace.SLOT_INPUT, BlockType.IRON_ORE, 1);
            TestMachine m = new TestMachine("after-removed", 3);
            // A "removed" machine type sits between the two known entities in the file.
            storage.save(new StubChunk(pos),
                    List.of(new ChunkStorage.BlockEntitySave(1, 2, 3, f),
                            new ChunkStorage.BlockEntitySave(10, 11, 12, new RemovedMachine()),
                            new ChunkStorage.BlockEntitySave(20, 21, 22, m)));

            List<ChunkStorage.BlockEntitySave> restored = storage.load(new StubChunk(pos));
            assertEquals(2, restored.size(), "removed type is dropped, known ones survive");
            assertTrue(restored.get(0).entity() instanceof Furnace);
            assertEquals(1, restored.get(0).x());
            assertEquals(20, restored.get(1).x(), "the entity after the removed one still parses");
            assertEquals("after-removed", ((TestMachine) restored.get(1).entity()).label());
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void fileWithNoEntitySectionLoadsWithNoEntities() throws IOException {
        Path dir = Files.createTempDirectory("mcclone-save-test");
        try {
            ChunkStorage storage = new ChunkStorage(dir);
            ChunkPos pos = new ChunkPos(0, 0);
            storage.save(new StubChunk(pos), List.of());
            StubChunk b = new StubChunk(pos);
            assertTrue(storage.load(b).isEmpty());
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void oldFormatFileWithNoOverlayHalfDefaultsOverlaysToEmpty() throws IOException {
        Path dir = Files.createTempDirectory("mcclone-save-test");
        try {
            ChunkStorage storage = new ChunkStorage(dir);
            ChunkPos pos = new ChunkPos(1, 1);
            StubChunk a = new StubChunk(pos);
            byte[] blocks = a.getRawBlocks();
            blocks[0] = BlockType.GRASS.id;
            // Write just the blocks half (no overlays, no entity section) - the
            // shape of a file saved before those sections existed.
            Path file = dir.resolve("chunks").resolve("c_1_1.chunk");
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(Files.newOutputStream(file))) {
                gz.write(blocks);
            }
            StubChunk b = new StubChunk(pos);
            assertTrue(storage.load(b).isEmpty());
            assertEquals(BlockType.GRASS.id, b.getRawBlocks()[0]);
            for (byte o : b.getRawOverlays()) {
                assertEquals(0, o, "overlays default to empty");
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void unknownTypeNameIsNotRegistered() {
        assertNull(BlockEntities.create("does_not_exist"));
        assertTrue(BlockEntities.isRegistered("furnace"));
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of the temp dir.
                }
            });
        }
    }
}
