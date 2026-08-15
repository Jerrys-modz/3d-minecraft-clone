package com.minecraftclone.world;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persists player-modified chunks to disk so edits survive a chunk being
 * unloaded (streamed out) and reloaded later, or the game being restarted
 * entirely. Unmodified chunks are never written - they're cheap to
 * regenerate deterministically from the world seed, which is what keeps an
 * "infinite" world's disk footprint bounded to only what the player actually
 * changed.
 * <p>
 * Each chunk is stored as a single gzip-compressed file: its raw block-id
 * bytes, followed by its raw overlay-id bytes (see {@link Chunk#getRawOverlays}
 * - e.g. seaweed growing inside a water cell), followed by any {@link Furnace}
 * state sitting in that chunk (see {@link FurnaceSave}). The first two halves
 * are mostly repeated values so the file compresses very well. Files written
 * before a section existed are still readable: a file with no overlay half
 * defaults every cell to no overlay, and a file with no furnace section has
 * no furnaces.
 */
public class ChunkStorage {

    /**
     * The minimal chunk surface {@link ChunkStorage} needs to read and write a
     * chunk's saved data. {@link Chunk} implements it; keeping the dependency
     * narrow (rather than on the whole GL-bound chunk) keeps this layer
     * testable without an OpenGL context.
     */
    public interface PersistableChunk {
        ChunkPos getPos();

        byte[] getRawBlocks();

        void setRawBlocks(byte[] data);

        byte[] getRawOverlays();

        void setRawOverlays(byte[] data);

        void markGenerated();
    }

    /** A furnace's block position and state, carried alongside the chunk it sits in. */
    public record FurnaceSave(int x, int y, int z, Furnace furnace) {
    }

    private final Path chunksDir;

    public ChunkStorage(Path worldDir) {
        this.chunksDir = worldDir.resolve("chunks");
        try {
            Files.createDirectories(chunksDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create world save directory: " + chunksDir, e);
        }
    }

    private Path fileFor(ChunkPos pos) {
        return chunksDir.resolve("c_" + pos.x() + "_" + pos.z() + ".chunk");
    }

    public boolean hasSavedChunk(ChunkPos pos) {
        return Files.isRegularFile(fileFor(pos));
    }

    /**
     * Loads a previously-saved chunk's block, overlay and furnace data. Caller
     * must have already checked {@link #hasSavedChunk}. Returns the furnaces
     * that were saved in the chunk (empty for files from before furnaces
     * existed); the caller registers them back into the world.
     */
    public List<FurnaceSave> load(PersistableChunk chunk) {
        Path file = fileFor(chunk.getPos());
        List<FurnaceSave> furnaces = new ArrayList<>();
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            byte[] data = in.readAllBytes();
            int blockLen = chunk.getRawBlocks().length;
            // A file from before overlays existed is just the blocks half - still
            // loads fine, every cell just defaults to no overlay.
            byte[] blockData = data.length >= blockLen ? java.util.Arrays.copyOfRange(data, 0, blockLen) : data;
            chunk.setRawBlocks(blockData);
            int overlayLen = chunk.getRawOverlays().length;
            if (data.length >= blockLen + overlayLen) {
                byte[] overlayData = java.util.Arrays.copyOfRange(data, blockLen, blockLen + overlayLen);
                chunk.setRawOverlays(overlayData);
            }
            // A file from before furnaces existed has no tail section.
            int offset = blockLen + overlayLen;
            if (data.length >= offset + 4) {
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(data, offset, data.length - offset));
                int count = d.readInt();
                for (int i = 0; i < count; i++) {
                    int x = d.readInt(), y = d.readInt(), z = d.readInt();
                    Furnace furnace = new Furnace();
                    furnace.readFrom(d);
                    furnaces.add(new FurnaceSave(x, y, z, furnace));
                }
            }
            chunk.markGenerated();
            // Note: deliberately NOT marking the chunk modified-by-player here - its
            // data is already on disk, so it only needs to be re-saved if the player
            // actually edits it (which setLocalFromPlayer/setOverlayFromPlayer handles).
        } catch (IOException e) {
            System.err.println("Failed to load saved chunk " + chunk.getPos() + ": " + e.getMessage());
        }
        return furnaces;
    }

    public void save(PersistableChunk chunk, List<FurnaceSave> furnaces) {
        Path file = fileFor(chunk.getPos());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(chunk.getRawBlocks());
            out.write(chunk.getRawOverlays());
            DataOutputStream d = new DataOutputStream(out);
            d.writeInt(furnaces.size());
            for (FurnaceSave fs : furnaces) {
                d.writeInt(fs.x());
                d.writeInt(fs.y());
                d.writeInt(fs.z());
                fs.furnace().writeTo(d);
            }
            d.flush();
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + chunk.getPos() + ": " + e.getMessage());
        }
    }
}
