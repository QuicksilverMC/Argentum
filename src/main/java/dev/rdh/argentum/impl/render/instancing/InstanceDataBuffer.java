package dev.rdh.argentum.impl.render.instancing;

import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;
import java.util.Arrays;

public abstract class InstanceDataBuffer {
    private final int stride;
    private final int positionOffset;
    private int[] data;
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
        this.sort(0, this.count - 1);
    }

    private void sort(int low, int high) {
        int left = low;
        int right = high;
        float pivot = this.distance((low + high) >>> 1);
        while (left <= right) {
            while (this.distance(left) > pivot) left++;
            while (this.distance(right) < pivot) right--;
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
        int a = first * this.stride;
        int b = second * this.stride;
        for (int i = 0; i < this.stride; i++) {
            int value = this.data[a + i];
            this.data[a + i] = this.data[b + i];
            this.data[b + i] = value;
        }
    }

    public final IntBuffer upload() {
        if (this.upload.capacity() < this.size) {
            this.upload = BufferUtils.createIntBuffer(this.data.length);
        }
        return this.upload.clear().put(this.data, 0, this.size).flip();
    }
}
