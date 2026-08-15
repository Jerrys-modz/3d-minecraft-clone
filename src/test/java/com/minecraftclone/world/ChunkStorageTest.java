package com.minecraftclone.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void savesAndRestoresBlocksOverlaysAndFurnaces() throws IOException {
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
            f.setSlot(Furnace.SLOT_FUEL, BlockType.COAL_ORE, 2);
            f.tick(3f);

            storage.save(a, List.of(new ChunkStorage.FurnaceSave(48, 40, -25, f)));

            StubChunk b = new StubChunk(pos);
            List<ChunkStorage.FurnaceSave> restored = storage.load(b);

            assertEquals(BlockType.FURNACE.id, b.getRawBlocks()[0]);
            assertEquals(BlockType.STONE.id, b.getRawBlocks()[10]);
            assertEquals(BlockType.SEAWEED.id, b.getRawOverlays()[5]);
            assertEquals(1, restored.size());
            ChunkStorage.FurnaceSave fs = restored.get(0);
            assertEquals(48, fs.x());
            assertEquals(40, fs.y());
            assertEquals(-25, fs.z());
            assertEquals(BlockType.GOLD_ORE, fs.furnace().typeOf(Furnace.SLOT_INPUT));
            assertEquals(4, fs.furnace().countOf(Furnace.SLOT_INPUT));
            assertEquals(BlockType.COAL_ORE, fs.furnace().typeOf(Furnace.SLOT_FUEL));
            assertEquals(1, fs.furnace().countOf(Furnace.SLOT_FUEL), "one of two coals burned during tick(3)");
            assertEquals(f.progressFraction(), fs.furnace().progressFraction(), 0.001f);
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    void fileWithNoFurnaceSectionLoadsWithNoFurnaces() throws IOException {
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
            // Write just the blocks half (no overlays, no furnace section) - the
            // shape of a file saved before overlays/furnaces existed.
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
