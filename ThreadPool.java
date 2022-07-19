package il.co.ilrd.threadpool;
/*
 * ThreadPool WS by Tanya Shk
 * April 24,2022,
 * reviewed by Adrian A.A.concurrency
 */



import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPool implements Executor {
    //protected for testing purposes!
    protected WaitablePriorityQueueSem<Task<?>> wpq;
    protected List<ThreadAction> threadsList;
    private int numOfThreadz;
    private static final int HIGHEST_PRIORITY = 100;
    private static final int LOWEST_PRIORITY = -5;
    private final Semaphore stopLightSem;

   public ThreadPool(){
        this(Runtime.getRuntime().availableProcessors() * 2);
   }
    public ThreadPool(int numberOfThreads) {
        if (numberOfThreads <= 0)
           throw new IllegalArgumentException();

        numOfThreadz = numberOfThreads;
        stopLightSem = new Semaphore(0);
        wpq = new WaitablePriorityQueueSem<>(numOfThreadz);
        threadsList = new LinkedList<>();

        for(int i = 0; i < numOfThreadz; ++i){
            threadsList.add(new ThreadAction());
            threadsList.get(i).start();
        }

    }

    public enum Priority {
        LOW,
        MED,
        HIGH
    }

    private final Callable<Void> shutItDown = () -> {
        ThreadAction shalter = (ThreadAction) ThreadAction.currentThread();
        shalter.isRunning.set(false);
         return null;
    };

    public Future<Void> submit(Runnable task, Priority priority) throws InterruptedException {
       return this.submit(Executors.callable(task,null), priority);
    }
    public <T> Future<T> submit(Runnable task, Priority priority, T returnValue) throws InterruptedException {
       return this.submit(Executors.callable(task,returnValue),priority);
    }
    public <T> Future<T> submit(Callable<T> task) throws InterruptedException {
        return this.submit(task, Priority.MED);
    }
    public <T> Future<T> submit(Callable<T> task, Priority priority) throws InterruptedException {
        if(isShut)
           throw new RejectedExecutionException();

        Task<T> createTask = new Task<>(task,priority.ordinal());
        wpq.enqueue(createTask);
        return createTask.futureHolder;
    }

    @Override
    public void execute(Runnable run) {
        try {
            this.submit(run, Priority.MED);
        } catch (InterruptedException e) {
            throw new RejectedExecutionException(e);
        }
    }

    public void setNumberOfThreads(int updateNumberOfThreads) {
        if(updateNumberOfThreads < 0)
            throw new IllegalArgumentException();

        int remainder = updateNumberOfThreads - numOfThreadz;
        if (remainder >= 0) {
            while (0 <= --remainder) {
                ThreadAction added = new ThreadAction();
                threadsList.add(added);
                added.start();
            }
        } else {
            while (0 > remainder++) {
                try {
                    wpq.enqueue(new Task<>(shutItDown, HIGHEST_PRIORITY));
                }catch(InterruptedException e){
                    System.err.println(e);
                }
            }
        }
        numOfThreadz = updateNumberOfThreads;
    }

    public void pause() throws InterruptedException {
        Callable<Void> pauseIt = () ->{
            stopLightSem.acquire();
            return null;
        };
        for(int i =0; i<numOfThreadz;++i) {
            wpq.enqueue(new Task<>(pauseIt,HIGHEST_PRIORITY));
        }
    }
    public void resume() {
        stopLightSem.release(numOfThreadz);
    }

    private volatile boolean isShut = false;
    public void shutdown() throws InterruptedException {

        isShut = true;
        for(int i = 0; i < numOfThreadz; ++i) {
            wpq.enqueue(new Task<>(shutItDown,LOWEST_PRIORITY));
        }
    }
    public void awaitTermination() throws InterruptedException {
        this.awaitTermination(Long.MAX_VALUE,TimeUnit.DAYS);
    }
    
    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        for (ThreadAction t : threadsList) {
            t.join(TimeUnit.MILLISECONDS.convert(timeout, unit)/numOfThreadz);
        }
        threadsList.clear();
    }

    private class Task<T> implements Comparable<Task<?>> {
        private final Integer realPriority;
        private final TaskFuture futureHolder;
        private final Callable<T> gullible;
        private T result;
        private final AtomicBoolean isTaskDone = new AtomicBoolean(false);
        public Task(Callable<T> gullible, Integer realPriority){
            this.realPriority = realPriority;
            this.gullible = gullible;
            futureHolder = new TaskFuture(Task.this);
        }

        void execute() {
           try {
               result = gullible.call();
           }catch(Exception e){
                e.printStackTrace();
           }
           isTaskDone.set(true);
           futureHolder.futureLock.lock();
           futureHolder.blockResult.signal();
           futureHolder.futureLock.unlock();
        }
        @Override
        public int compareTo(Task<?> task) {
            return task.realPriority.compareTo(this.realPriority);
        }

            private class TaskFuture implements Future<T>{
            private final ReentrantLock futureLock = new ReentrantLock();
            private final Condition blockResult = futureLock.newCondition();
            private final AtomicBoolean statusTaskFuture = new AtomicBoolean();
            //private final Task<T> holder;

            public TaskFuture(Task<T> newTask){

                 newTask = Task.this;
                //holder = newTask;
            }

            @Override
            public boolean cancel(boolean b) {
                 boolean trial;
                if(isCancelled()) {
                    return false;
                }
                try {
                    trial = wpq.remove(Task.this);
                } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                }
                if(trial){
                    statusTaskFuture.set(true);
                    isTaskDone.set(true);
                }
                return trial;
            }

            @Override
            public boolean isCancelled() {
                return statusTaskFuture.get();
            }
            @Override
            public boolean isDone() {
               return isTaskDone.get();
            }

            @Override
            public T get() throws InterruptedException {
                try {
                    return get(Long.MAX_VALUE,TimeUnit.DAYS);
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public T get(long l, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
                futureLock.lock();
                if(!isDone()){
                    blockResult.await(l,timeUnit);
                }
                futureLock.unlock();
                return result;
            }
        }
    }

    private class ThreadAction extends Thread {
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
        @Override
        public void run(){
            while(isRunning.get()) {
                Task<?> toPerform;
                try {
                    toPerform = wpq.dequeue();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                toPerform.execute();
            }
        }
    }
}
