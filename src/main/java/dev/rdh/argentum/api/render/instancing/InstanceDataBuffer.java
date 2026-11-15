package dev.rdh.argentum.api.render.instancing;

import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;
import java.util.Arrays;

public abstract class InstanceDataBuffer {
    private final int stride;
    private final int positionOffset;
    private int[] data;
    private int[] scratch = new int[0];
    private float[] keys = new float[0];
    private int[] order = new int[0];
    private IntBuffer upload;
    private int size;
    private int count;

    protected InstanceDataBuffer(int stride, int positionOffset, int initialCapacity) {
        this.stride = stride;
        this.positionOffset = positionOffset;
        this.data = new int[initialCapacity * stride];
        this.upload = BufferUtils.createIntBuffer(this.data.length);
    }

    public final void clear() {
        this.size = 0;
        this.count = 0;
    }

    protected final int appendOffset() {
        if (this.size + this.stride > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
        }
        return this.size;
    }

    protected final int[] data() {
        return this.data;
    }

    protected final void finishInstance() {
        this.size += this.stride;
        this.count++;
    }

    public final int count() {
        return this.count;
    }

    public final void sortBackToFront() {
        int count = this.count;
        if (count < 2) return;
        if (this.keys.length < count) {
            this.keys = new float[this.data.length / this.stride];
            this.order = new int[this.keys.length];
        }
        for (int i = 0; i < count; i++) {
            this.keys[i] = this.distance(i);
            this.order[i] = i;
        }
        this.sort(0, count - 1);

        if (this.scratch.length < this.data.length) this.scratch = new int[this.data.length];
        for (int i = 0; i < count; i++) {
            System.arraycopy(this.data, this.order[i] * this.stride, this.scratch, i * this.stride, this.stride);
        }
        int[] sorted = this.scratch;
        this.scratch = this.data;
        this.data = sorted;
    }

    private void sort(int low, int high) {
        float[] keys = this.keys;
        int left = low;
        int right = high;
        float pivot = keys[(low + high) >>> 1];
        while (left <= right) {
            while (keys[left] > pivot) left++;
            while (keys[right] < pivot) right--;
            if (left <= right) {
                this.swap(left++, right--);
            }
        }
        if (low < right) this.sort(low, right);
        if (left < high) this.sort(left, high);
    }

    private float distance(int index) {
        int offset = index * this.stride + this.positionOffset;
        float x = Float.intBitsToFloat(this.data[offset]);
        float y = Float.intBitsToFloat(this.data[offset + 1]);
        float z = Float.intBitsToFloat(this.data[offset + 2]);
        return x * x + y * y + z * z;
    }

    private void swap(int first, int second) {
        float key = this.keys[first];
        this.keys[first] = this.keys[second];
        this.keys[second] = key;
        int index = this.order[first];
        this.order[first] = this.order[second];
        this.order[second] = index;
    }

    public final IntBuffer upload() {
        if (this.upload.capacity() < this.size) {
            this.upload = BufferUtils.createIntBuffer(this.data.length);
        }
        return this.upload.clear().put(this.data, 0, this.size).flip();
    }
}
