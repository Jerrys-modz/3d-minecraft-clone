package com.minecraftclone.util;

/**
 * A growable primitive {@code int} array, avoiding the per-element boxing of
 * {@code ArrayList<Integer>}. See {@link FloatArray} for usage; the two are
 * typically kept in lockstep as a vertex/index pair.
 */
public final class IntArray {

    private int[] data;
    private int size;

    public IntArray() {
        this(64);
    }

    public IntArray(int initialCapacity) {
        this.data = new int[Math.max(8, initialCapacity)];
    }

    public void add(int value) {
        if (size == data.length) {
            int[] bigger = new int[data.length * 2];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }
        data[size++] = value;
    }

    public int get(int index) {
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
    public int[] toArray() {
        int[] copy = new int[size];
        System.arraycopy(data, 0, copy, 0, size);
        return copy;
    }
}
