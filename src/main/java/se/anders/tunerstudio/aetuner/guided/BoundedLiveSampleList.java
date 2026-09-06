package se.anders.tunerstudio.aetuner.guided;

import se.anders.tunerstudio.aetuner.model.LiveSample;

import java.util.AbstractList;
import java.util.RandomAccess;

/** Fixed-capacity logical list with constant-time head removal. */
final class BoundedLiveSampleList extends AbstractList<LiveSample>
        implements RandomAccess {
    private final LiveSample[] elements;
    private int head;
    private int size;

    BoundedLiveSampleList(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        elements = new LiveSample[capacity];
    }

    @Override public LiveSample get(int index) {
        checkIndex(index);
        return elements[physical(index)];
    }

    @Override public int size() { return size; }

    @Override public boolean add(LiveSample sample) {
        if (size >= elements.length) {
            throw new IllegalStateException("bounded sample list is full; remove oldest before add");
        }
        elements[physical(size)] = sample;
        size++;
        modCount++;
        return true;
    }

    @Override public LiveSample remove(int index) {
        checkIndex(index);
        LiveSample removed = get(index);
        if (index == 0) {
            elements[head] = null;
            head = (head + 1) % elements.length;
            size--;
            if (size == 0) head = 0;
            modCount++;
            return removed;
        }

        // Only head removal is used by Guided retention. Supporting arbitrary
        // removal keeps List semantics correct without putting that cost on the
        // high-rate path.
        for (int i = index; i < size - 1; i++) {
            elements[physical(i)] = elements[physical(i + 1)];
        }
        elements[physical(size - 1)] = null;
        size--;
        if (size == 0) head = 0;
        modCount++;
        return removed;
    }

    @Override public void clear() {
        for (int i = 0; i < size; i++) elements[physical(i)] = null;
        head = 0;
        size = 0;
        modCount++;
    }

    private int physical(int logicalIndex) {
        return (head + logicalIndex) % elements.length;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }
}
