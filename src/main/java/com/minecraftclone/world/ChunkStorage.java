package com.minecraftclone.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * - e.g. seaweed growing inside a water cell), followed by any {@link BlockEntity}
 * state sitting in that chunk (furnaces today - see {@link BlockEntitySave}).
 * The first two halves are mostly repeated values so the file compresses very
 * well. Files written before a section existed are still readable: a file with
 * no overlay half defaults every cell to no overlay, and a file with no block
 * entity section has none.
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

    /** A block entity's block position and state, carried alongside the chunk it sits in. */
    public record BlockEntitySave(int x, int y, int z, BlockEntity entity) {
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
     * Loads a previously-saved chunk's block, overlay and block-entity data.
     * Caller must have already checked {@link #hasSavedChunk}. Returns the block
     * entities that were saved in the chunk (empty for files from before block
     * entities existed); the caller registers them back into the world.
     */
    public List<BlockEntitySave> load(PersistableChunk chunk) {
        Path file = fileFor(chunk.getPos());
        List<BlockEntitySave> entities = new ArrayList<>();
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
            // A file from before block entities existed has no tail section.
            int offset = blockLen + overlayLen;
            if (data.length >= offset + 4) {
                DataInputStream d = new DataInputStream(new ByteArrayInputStream(data, offset, data.length - offset));
                int count = d.readInt();
                for (int i = 0; i < count; i++) {
                    String type = d.readUTF();
                    int x = d.readInt(), y = d.readInt(), z = d.readInt();
                    int payloadLen = d.readInt();
                    BlockEntity entity = BlockEntities.create(type);
                    if (entity != null) {
                        entity.readFrom(d);
                        entities.add(new BlockEntitySave(x, y, z, entity));
                    } else {
                        // Unknown/removed type: skip its payload so the rest of the
                        // file stays readable.
                        long remaining = payloadLen;
                        while (remaining > 0) {
                            long skipped = d.skipBytes((int) Math.min(Integer.MAX_VALUE, remaining));
                            if (skipped <= 0) break;
                            remaining -= skipped;
                        }
                    }
                }
            }
            chunk.markGenerated();
            // Note: deliberately NOT marking the chunk modified-by-player here - its
            // data is already on disk, so it only needs to be re-saved if the player
            // actually edits it (which setLocalFromPlayer/setOverlayFromPlayer handles).
        } catch (IOException e) {
            System.err.println("Failed to load saved chunk " + chunk.getPos() + ": " + e.getMessage());
        }
        return entities;
    }

    public void save(PersistableChunk chunk, List<BlockEntitySave> entities) {
        Path file = fileFor(chunk.getPos());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(chunk.getRawBlocks());
            out.write(chunk.getRawOverlays());
            DataOutputStream d = new DataOutputStream(out);
            d.writeInt(entities.size());
            for (BlockEntitySave es : entities) {
                d.writeUTF(es.entity().type());
                d.writeInt(es.x());
                d.writeInt(es.y());
                d.writeInt(es.z());
                // Length-prefixed payload: lets an unknown type on load skip
                // exactly its own bytes without breaking the rest of the file.
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                es.entity().writeTo(new DataOutputStream(payload));
                byte[] bytes = payload.toByteArray();
                d.writeInt(bytes.length);
                d.write(bytes);
            }
            d.flush();
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + chunk.getPos() + ": " + e.getMessage());
        }
    }
}
