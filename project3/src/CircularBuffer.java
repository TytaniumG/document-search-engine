/*
 * A minimal circular buffer implementation backed by a dynamically resized array.
 */

public class CircularBuffer<E> {
    private static final int INITIAL_CAPACITY = 8;
    
    private E[] buffer;
    private int head;
    private int tail;
    private int size;
    
    // constructs an empty circular buffer with initial capacity of 8
    public CircularBuffer() {
        buffer = (E[]) new Object[INITIAL_CAPACITY];
        head = 0;
        tail = 0;
        size = 0;
    }
    
    // appends an element to the end of the buffer
    public void addLast(E element) {
        // resize if buffer is full
        if (size == buffer.length) {
            // double the capacity
            E[] oldBuffer = buffer;
            int oldCapacity = oldBuffer.length;
            int newCapacity = oldCapacity * 2;
        
            buffer = (E[]) new Object[newCapacity];
        
            // copy elements in order from old buffer to new buffer
            for (int i = 0; i < size; i++) {
                buffer[i] = oldBuffer[(head + i) % oldCapacity];
            }
        
            // reset head and tail
            head = 0;
            tail = size;
        }
        
        buffer[tail] = element;
        tail = (tail + 1) % buffer.length;
        size++;
    }
    
    // removes and returns the most recently added element
    public E removeLast() {
        if (size == 0) {
            throw new IllegalStateException("Cannot remove from empty buffer");
        }
        
        tail = (tail - 1 + buffer.length) % buffer.length;
        E element = buffer[tail];
        buffer[tail] = null;
        size--;
        return element;
    }
    
    // removes and returns the oldest added element
    public E removeFirst() {
        if (size == 0) {
            throw new IllegalStateException("Cannot remove from empty buffer");
        }
        
        E element = buffer[head];
        buffer[head] = null;
        head = (head + 1) % buffer.length;
        size--;
        return element;
    }
    
    // returns the number of elements currently stored in the buffer
    public int size() {
        return size;
    }
    
    // returns an iterator over the elements from oldest to newest
    public CircularBufferIterator iterator() {
        return new CircularBufferIterator();
    }
    
    // iterator that traverses from oldest to newest element
    public class CircularBufferIterator {
        private int currentIndex = 0;
        public boolean hasNext() {
            return currentIndex < size;
        }
        
        // returns the next element in iteration order
        public E next() {
            if (!hasNext()) {
                throw new IllegalStateException("No more elements");
            }
            E element = buffer[(head + currentIndex) % buffer.length];
            currentIndex++;
            return element;
        }
    }
}
