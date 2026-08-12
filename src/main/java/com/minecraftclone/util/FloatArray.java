package com.minecraftclone.util;

/**
 * A growable primitive {@code float} array, avoiding the per-element boxing of
 * {@code ArrayList<Float>}. Backing storage grows geometrically and is reused
 * across frames via {@link #clear()} - intended for hot per-frame buffers like
 * HUD/text vertex batches.
 */
public final class FloatArray {

    private float[] data;
    private int size;

    public FloatArray() {
        this(64);
    }

    public FloatArray(int initialCapacity) {
        this.data = new float[Math.max(8, initialCapacity)];
    }

    public void add(float value) {
        if (size == data.length) {
            float[] bigger = new float[data.length * 2];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }
        data[size++] = value;
    }

    public float get(int index) {
        return data[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        size = 0;
    }

    /** A copy of the current contents (the backing array is larger than {@link #size()}). */
    public float[] toArray() {
        float[] copy = new float[size];
        System.arraycopy(data, 0, copy, 0, size);
        return copy;
    }
}
