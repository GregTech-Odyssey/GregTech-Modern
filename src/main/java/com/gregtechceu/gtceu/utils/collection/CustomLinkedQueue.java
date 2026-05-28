package com.gregtechceu.gtceu.utils.collection;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

public class CustomLinkedQueue<T extends CustomLinkedQueue.LinkNode<T>> implements Iterable<T> {

    @Nullable
    protected T first;
    @Nullable
    protected T last;
    protected int size;

    public boolean isEmpty() {
        return first == null;
    }

    public int size() {
        return size;
    }

    @Nullable
    public T peekFirst() {
        return first;
    }

    @Nullable
    public T peekLast() {
        return last;
    }

    public void addLast(@NotNull T node) {
        final T l = last;
        if (l == null) {
            first = node;
        } else {
            l.setNext(node);
            node.setPrev(l);
        }
        last = node;
        size++;
    }

    public void addFirst(@NotNull T node) {
        final T f = first;
        if (f == null) {
            last = node;
        } else {
            f.setPrev(node);
            node.setNext(f);
        }
        first = node;
        size++;
    }

    public void unlink(@NotNull T node) {
        final T prev = node.getPrev();
        final T next = node.getNext();
        if (prev == null) {
            first = next;
        } else {
            prev.setNext(next);
            node.setPrev(null);
        }
        if (next == null) {
            last = prev;
        } else {
            next.setPrev(prev);
            node.setNext(null);
        }
        size--;
    }

    public boolean remove(@NotNull T node) {
        if (node.getPrev() != null || node.getNext() != null || node == first) {
            unlink(node);
            return true;
        }
        return false;
    }

    @Nullable
    public T pollFirst() {
        final T f = first;
        if (f == null) return null;
        unlink(f);
        return f;
    }

    @Nullable
    public T pollLast() {
        final T l = last;
        if (l == null) return null;
        unlink(l);
        return l;
    }

    @NotNull
    public T removeFirst() {
        final T f = pollFirst();
        if (f == null) throw new NoSuchElementException();
        return f;
    }

    @NotNull
    public T removeLast() {
        final T l = pollLast();
        if (l == null) throw new NoSuchElementException();
        return l;
    }

    public void merge(@NotNull CustomLinkedQueue<T> other) {
        if (other.first == null || other == this) return;
        final T otherFirst = other.first;
        final T otherLast = other.last;
        if (last == null) {
            first = otherFirst;
        } else {
            last.setNext(otherFirst);
            otherFirst.setPrev(last);
        }
        last = otherLast;
        size += other.size;
        other.first = null;
        other.last = null;
        other.size = 0;
    }

    public void clear() {
        for (T node = first; node != null;) {
            T next = node.getNext();
            node.setPrev(null);
            node.setNext(null);
            node = next;
        }
        first = null;
        last = null;
        size = 0;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return new QueueIterator();
    }

    public boolean contains(T node) {
        for (T n = first; n != null; n = n.getNext()) {
            if (n == node) return true;
        }
        return false;
    }

    @NotNull
    public Object[] toArray() {
        Object[] arr = new Object[size];
        int i = 0;
        for (T n = first; n != null; n = n.getNext()) {
            arr[i++] = n;
        }
        return arr;
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (T n = first; n != null; n = n.getNext()) {
            action.accept(n);
        }
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), size, Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL);
    }

    private final class QueueIterator implements Iterator<T> {

        @Nullable
        private T nextNode = first;
        @Nullable
        private T currentNode;

        @Override
        public boolean hasNext() {
            return nextNode != null;
        }

        @Override
        public T next() {
            var nextNode = this.nextNode;
            if (nextNode == null) throw new NoSuchElementException();
            this.currentNode = nextNode;
            this.nextNode = nextNode.getNext();
            return nextNode;
        }

        @Override
        public void remove() {
            var currentNode = this.currentNode;
            if (currentNode == null) throw new IllegalStateException();
            CustomLinkedQueue.this.unlink(currentNode);
            this.currentNode = null;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            while (hasNext()) action.accept(next());
        }
    }

    public interface LinkNode<T extends LinkNode<T>> {

        @Nullable
        T getPrev();

        void setPrev(@Nullable T prev);

        @Nullable
        T getNext();

        void setNext(@Nullable T next);
    }

    public abstract static class AbstractLinkNode<T extends AbstractLinkNode<T>> implements LinkNode<T> {

        @Setter
        @Getter
        protected T prev;

        @Setter
        @Getter
        protected T next;
    }
}
