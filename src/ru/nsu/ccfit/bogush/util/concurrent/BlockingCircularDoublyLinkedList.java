package ru.nsu.ccfit.bogush.util.concurrent;

public class BlockingCircularDoublyLinkedList<E> {
    private static final int MAX_CAPACITY = Integer.MAX_VALUE;

    private class ListNode {
        private E elem;
        private ListNode next;
        private ListNode prev;

        private ListNode(E elem) {
            this.elem = elem;
            next = prev = this;
        }
    }

    private ListNode head = null;
    private final int capacity;
    private int size = 0;

    private final Object monitor = new Object();

    public BlockingCircularDoublyLinkedList() {
        this(MAX_CAPACITY);
    }

    public BlockingCircularDoublyLinkedList(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
    }

    private void putAfter(ListNode node, E elem)
            throws InterruptedException {
        synchronized (monitor) {
            while (size == capacity) {
                monitor.wait();
            }

            ++size;

            if (head == null) {
                head = new ListNode(elem);
                size = 1;
            } else {
                ListNode newNode = new ListNode(elem);
                newNode.next = node.next;
                newNode.prev = node;
                node.next = newNode;
                ++size;
            }

            monitor.notifyAll();
        }
    }

    public void putNext(E elem)
            throws InterruptedException {
        putAfter(head, elem);
    }

    public void putPrev(E elem)
            throws InterruptedException {
        putAfter(head.prev, elem);
    }

    public E take()
            throws InterruptedException {
        E e;
        synchronized (monitor) {
            while (size == 0) {
                monitor.wait();
            }

            e = head.elem;

            if (size == 1) {
                head = null;
            } else {
                head.prev.next = head.next;
                head.next.prev = head.prev;
                head = head.next;
            }

            --size;
            monitor.notifyAll();
        }
        return e;
    }

    public E peek() {
        synchronized (monitor) {
            return head == null ? null : head.elem;
        }
    }

    public E next()
            throws InterruptedException {
        synchronized (monitor) {
            while (head == null) {
                monitor.wait();
            }
            head = head.next;
            return head.elem;
        }
    }

    public E prev()
            throws InterruptedException {
        synchronized (monitor) {
            while (head == null) {
                monitor.wait();
            }
            head = head.prev;
            return head.elem;
        }
    }

    public boolean isEmpty() {
        synchronized (monitor) {
            return size == 0;
        }
    }
}
