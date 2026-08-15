package com.minecraftclone.player;

import com.minecraftclone.world.BlockType;

/**
 * Presents two {@link StorageContainer}s as a single, larger one - slot
 * {@code i} maps to the first container for {@code i < first.size()}, and to
 * the second for the rest. Used for a Minecraft-style double chest (two
 * adjacent 27-slot chests shown as one 54-slot container), and the building
 * block a future item network will compose many storages into a single view.
 * <p>
 * The two halves stay separate storage (each persists its own slots), so
 * composing them costs nothing and needs no new persistence: the player just
 * sees one bigger container.
 */
public final class JoinedStorage implements StorageContainer {

    private final StorageContainer first;
    private final StorageContainer second;

    public JoinedStorage(StorageContainer first, StorageContainer second) {
        this.first = first;
        this.second = second;
    }

    public StorageContainer first() {
        return first;
    }

    public StorageContainer second() {
        return second;
    }

    @Override
    public int size() {
        return first.size() + second.size();
    }

    @Override
    public BlockType typeOf(int slot) {
        int f = first.size();
        if (slot < 0) return null;
        return slot < f ? first.typeOf(slot) : second.typeOf(slot - f);
    }

    @Override
    public int countOf(int slot) {
        int f = first.size();
        if (slot < 0) return 0;
        return slot < f ? first.countOf(slot) : second.countOf(slot - f);
    }

    @Override
    public void setSlot(int slot, BlockType type, int count) {
        int f = first.size();
        if (slot < 0) return;
        if (slot < f) {
            first.setSlot(slot, type, count);
        } else {
            second.setSlot(slot - f, type, count);
        }
    }

    @Override
    public int add(BlockType type, int amount) {
        int leftover = first.add(type, amount);
        if (leftover > 0) leftover = second.add(type, leftover);
        return leftover;
    }

    @Override
    public int getCount(BlockType type) {
        return first.getCount(type) + second.getCount(type);
    }
}
