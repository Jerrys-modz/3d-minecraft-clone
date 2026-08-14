package com.minecraftclone.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * - e.g. seaweed growing inside a water cell), both mostly repeated values so
 * it compresses very well. A file written before overlays existed is just the
 * first half - still loads fine, with every cell defaulting to no overlay.
 */
public class ChunkStorage {

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

    /** Loads a previously-saved chunk's block (and overlay) data into {@code chunk}. Caller must have already checked {@link #hasSavedChunk}. */
    public void load(Chunk chunk) {
        Path file = fileFor(chunk.getPos());
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            byte[] data = in.readAllBytes();
            int blockLen = chunk.getRawBlocks().length;
            // A file from before overlays existed is just the blocks half - still
            // loads fine, every cell just defaults to no overlay.
            byte[] blockData = data.length >= blockLen ? java.util.Arrays.copyOfRange(data, 0, blockLen) : data;
            chunk.setRawBlocks(blockData);
            if (data.length >= blockLen + chunk.getRawOverlays().length) {
                byte[] overlayData = java.util.Arrays.copyOfRange(data, blockLen, blockLen + chunk.getRawOverlays().length);
                chunk.setRawOverlays(overlayData);
            }
            chunk.markGenerated();
            // Note: deliberately NOT marking the chunk modified-by-player here - its
            // data is already on disk, so it only needs to be re-saved if the player
            // actually edits it (which setLocalFromPlayer/setOverlayFromPlayer handles).
        } catch (IOException e) {
            System.err.println("Failed to load saved chunk " + chunk.getPos() + ": " + e.getMessage());
        }
    }

    public void save(Chunk chunk) {
        Path file = fileFor(chunk.getPos());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(chunk.getRawBlocks());
            out.write(chunk.getRawOverlays());
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + chunk.getPos() + ": " + e.getMessage());
        }
    }
}
