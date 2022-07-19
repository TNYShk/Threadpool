package il.co.ilrd.threadpool;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class WaitablePriorityQueueSem<E> {
    private final PriorityQueue<E> myQ;
    private final Semaphore Qsem;
    private final Semaphore DQSem;
    private final ReentrantLock lock = new ReentrantLock();
    private static final int INITCAP = 11;

    private final int MAXCAPACITY;

    public WaitablePriorityQueueSem(int maxcapacity) {
        this(null,INITCAP,maxcapacity);
    }

    public WaitablePriorityQueueSem(int initialCapacity, int maxcapacity) {
        this(null,initialCapacity, maxcapacity);
    }

    public WaitablePriorityQueueSem(Comparator<? super E> compare, int maxcapacity) {
       this(compare,INITCAP, maxcapacity);
    }
    public WaitablePriorityQueueSem(Comparator<? super E> compare, int initialCapacity, int maxcapacity) {
        if(maxcapacity < INITCAP){
            maxcapacity = INITCAP;}

        MAXCAPACITY = maxcapacity;
        myQ =  new PriorityQueue<>(initialCapacity, compare);
        Qsem = new Semaphore(MAXCAPACITY);
        DQSem = new Semaphore(0);

    }

    //thread safe
    public void enqueue(E element) throws InterruptedException {
        Qsem.acquire();
        lock.lock();
        try {
            myQ.add(element);
            DQSem.release();
            //System.out.println(Thread.currentThread().getName() + " Q'd here  " + myQ.peek());
        } finally {
            lock.unlock();
        }
    }

    public E dequeue() throws InterruptedException {
        E deQ;
        DQSem.acquire();
        lock.lock();
        try {
            deQ = myQ.poll();
            //System.out.println(Thread.currentThread().getName() + " DQ'd here");
        }finally {
            lock.unlock();
        }

        Qsem.release();
        return deQ;
    }

    //thread safe
    public boolean remove(E element) throws InterruptedException {
        boolean found = DQSem.tryAcquire();
        if(!found) { return false;}

        lock.lock();
        try {
            found = myQ.remove(element);

            if (found) {
                Qsem.release();
            } else {
                DQSem.release();
            }
        }finally {
            lock.unlock();
        }
        return found;
    }

    public int size() {
      return myQ.size();
    }

    public boolean isEmpty() {
        return myQ.isEmpty();
    }


}


