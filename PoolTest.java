package il.co.ilrd.threadpool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PoolTest {

    @Test
    public void testSubmit1Arg() {

        ThreadPool threadsList = new ThreadPool(3);

        Callable<Integer> task = () -> (1 + 2);

        try {
            Future<Integer> result = threadsList.submit(task);
            System.out.println(result.get());
            assertEquals(3, result.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void Submit2Arg() {
        Runnable run1 = () -> System.out.println("JUNIT test2 Hello");
        ThreadPool pool = new ThreadPool(2);

        try {
            pool.submit(run1, ThreadPool.Priority.HIGH);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testSubmit() throws InterruptedException, ExecutionException, TimeoutException {
        Callable<Integer> call1 = () -> 1;
        Callable<Integer> call2 = () -> 2;
        Callable<Integer> call3 = () -> 3;
        Callable<Double> complexCalc = () -> (1.0 - (1.0/3.0) + (1.0/5.0) - (1.0 /7.0) + (1.0/9.0));
        Runnable run1 = () -> System.out.println("im a runner...");
        Runnable shutter = () -> System.out.println("im done! dont look at me");
        ThreadPool tp = new ThreadPool(3);
        Double answer = 42.0;
        Runnable run = () -> System.out.println("the answer is " + answer);
        Future<Double> c1 = tp.submit(complexCalc, ThreadPool.Priority.MED);
        Future<Integer> f1 = tp.submit(call1, ThreadPool.Priority.LOW);
        Future<Integer> f2 = tp.submit(call2, ThreadPool.Priority.HIGH);
        Future<Integer> f3 = tp.submit(call3);
        Future<Void> r2 = tp.submit(run1, ThreadPool.Priority.HIGH);
        Future<Double> r1 = tp.submit(run, ThreadPool.Priority.HIGH, answer);
        Future<Long> future = tp.submit(() -> {
            long sum = 0;
            int block = 5000;
            while (--block > 0); //busy wait

            for (long i = 0; i <= 10000000L; ++i) {
                sum += i;
            }
            return sum;
        }, ThreadPool.Priority.HIGH);
        System.out.println((future.get(20L, TimeUnit.SECONDS)));
        Assertions.assertTrue(f1.get().equals(1));
        assertTrue(f2.get().equals(2));
        assertTrue(f3.get().equals(3));

        future.cancel(false);
        assertTrue(future.isDone());

        assertTrue(c1.get(10l, TimeUnit.SECONDS).equals(0.8349206349206351));
        assertTrue(r2.get() == null);
        assertTrue(r1.get() == 42.0);
        tp.shutdown();

        tp.awaitTermination(5,TimeUnit.SECONDS);

        System.out.println("threadpool: "+ tp.threadsList.size() + " pqsize: " + tp.wpq.size());
        try {
            Thread.sleep(5000);
            tp.submit(shutter, ThreadPool.Priority.HIGH);
        }catch (RejectedExecutionException e){
            e.printStackTrace();
        }

        System.out.println("threadpool: "+ tp.threadsList.size() + " pqsize: " + tp.wpq.size());
    }

    @Test
    void cancelTest() throws InterruptedException, ExecutionException {
        ThreadPool tp = new ThreadPool(1);
        Callable HighPri = () -> {
            Integer ret = 13;
            System.out.println("hello from a high priority task " + Thread.currentThread().getId());
            Thread.sleep(5000);
            return ret;
        };
        Callable<Integer> call2 = () -> 2;
        Callable<Integer> call3 = () -> 3;
        Callable toCancel = (Callable) () -> {
            Integer ret = 13;
            Thread.sleep(1000);
            System.out.println("going to be canceled " + Thread.currentThread().getId());
            return ret;
        };
        Future<Integer> f1 = tp.submit(call2, ThreadPool.Priority.LOW);
        Future<Integer> f2 = tp.submit(call3, ThreadPool.Priority.MED);
        Future foo = tp.submit(HighPri);
        Future barf = tp.submit(toCancel, ThreadPool.Priority.LOW);
        barf.cancel(false);
        Thread.sleep(5000l);
        assertTrue(f1.get().equals(2));
        assertTrue(f2.get().equals(3));

        assertEquals(foo.isDone(), true);
        assertEquals(barf.get(), null);
        assertEquals(barf.isCancelled(), true);
    }

    @Test
    void priorityTest() throws InterruptedException {
        ThreadPool tp = new ThreadPool(1);
        tp.submit(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }, ThreadPool.Priority.MED);

        for (int i = 0; i < 3; ++i) {
            tp.submit(() -> System.out.print("LOW  "), ThreadPool.Priority.LOW);
            tp.submit(() -> System.out.print("MED  "), ThreadPool.Priority.MED);
            tp.submit(() -> System.out.print("anotherMED  "), ThreadPool.Priority.MED);
            tp.submit(() -> System.out.print("HIGH  "), ThreadPool.Priority.HIGH);
            tp.submit(() -> System.out.print("anotherHIGH  "), ThreadPool.Priority.HIGH);
        }

        Thread.sleep(6000);
    }

    @Test
    void futureTest() throws InterruptedException, ExecutionException, TimeoutException {
        Callable<String> c1 = () -> {
            String str = "a";
            while (str.length() < 5) {
                Thread.sleep(500);
                str += "a";
            }
            return str;
        };
        ThreadPool tp = new ThreadPool(1);
        Future<String> f1 = tp.submit(c1, ThreadPool.Priority.HIGH);
        assertFalse(f1.isDone());
        assertTrue(f1.get(500, TimeUnit.MILLISECONDS) == null);
        Thread.sleep(3000);
        assertTrue(f1.isDone());
        assertTrue(f1.get().equals("aaaaa"));
        Callable<String> c2 = () -> "if u see me, your future isn't good :/";
        tp.submit(c1, ThreadPool.Priority.HIGH);
        Future<String> f2 = tp.submit(c2, ThreadPool.Priority.HIGH);
        assertFalse(f2.isCancelled());
        assertTrue(f2.cancel(false));
        assertTrue(f2.isCancelled());
        assertTrue(f2.isDone());
        assertFalse(f2.cancel(false));
    }
    @Test
    void pauseAndResume() throws InterruptedException {
        ThreadPool tp = new ThreadPool(2);
        for (int i = 0; i < 10; ++i) {
            tp.submit(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {}
                System.out.println("i was here haha");
            }, ThreadPool.Priority.LOW);
        }
        Thread.sleep(1000); // let first two tasks execute
        tp.pause();
        Thread.sleep(5000); // semaphore is blocking the tasks
        tp.resume();
        Thread.sleep(5000); // tasks should return to execute normally*/
        tp.shutdown();
        tp.awaitTermination();
    }

    @Test
    void setNumberOfThreadsIncrease() throws InterruptedException {
        ThreadPool tp = new ThreadPool(1);
        assertTrue(tp.threadsList.size() == 1);

        for (int i = 0; i < 10; ++i) {
            tp.submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                System.out.println(Thread.currentThread().getName());
            }, ThreadPool.Priority.LOW);
        }
        Thread.sleep(3000);
        tp.setNumberOfThreads(2);
        assertTrue(tp.threadsList.size() == 2);

        tp.shutdown();
        tp.awaitTermination();

    }

    @Test
    void decreaseNumberOfThreadsTest() throws InterruptedException {
        ThreadPool tp = new ThreadPool(10);
        assertTrue(tp.threadsList.size() == 10);

        for (int i = 0; i < 20; ++i) {
            tp.submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                System.out.println(Thread.currentThread().getName());
            }, ThreadPool.Priority.LOW);
        }

        tp.setNumberOfThreads(2);
        Thread.sleep(3000);

        tp.shutdown();
        tp.awaitTermination();
        assertTrue(tp.threadsList.size() == 0);
        assertTrue(tp.wpq.isEmpty());
    }
    @Test
    void decreaseEmptyCtorTest() throws InterruptedException {
        ThreadPool tp = new ThreadPool();
        assertTrue(tp.threadsList.size() == 16);


        for (int i = 0; i < 20; ++i) {
            tp.submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                System.out.println(Thread.currentThread().getName());
            }, ThreadPool.Priority.LOW);
        }

        tp.setNumberOfThreads(2);
        Thread.sleep(3000);

        tp.shutdown();
        tp.awaitTermination();
        assertTrue(tp.threadsList.size() == 0);
        assertTrue(tp.wpq.isEmpty());
    }

    @Test
    void IllegualTest() throws InterruptedException {
        ThreadPool tp = new ThreadPool();
        assertTrue(tp.threadsList.size() == 16);

        for (int i = 0; i < 20; ++i) {
            tp.submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                System.out.println(Thread.currentThread().getName());
            }, ThreadPool.Priority.LOW);
        }
        try {
            tp.setNumberOfThreads(-9);
        }catch(Exception e){
            System.err.println(e);
        }

        tp.shutdown();
        tp.awaitTermination();
        assertTrue(tp.threadsList.size() == 0);
        assertTrue(tp.wpq.isEmpty());
    }

    @Test
    void awaitTerminationTime() throws InterruptedException {
        ThreadPool tp = new ThreadPool(3);
        for (int i = 0; i < 15; ++i) {
            tp.submit(() -> {
                System.out.println("wait for me!!");
            }, ThreadPool.Priority.LOW);
        }
        try {
            System.out.println(java.time.LocalTime.now());
            tp.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {

            e.printStackTrace();
        }
        System.out.println(java.time.LocalTime.now());
        assertTrue((tp.threadsList.isEmpty()));

    }

    @Test
    void TimeOut() throws InterruptedException, ExecutionException, TimeoutException {
        ThreadPool tp = new ThreadPool(1);
        Future<Integer> f1 = tp.submit(() -> {
            while(true);
        }, ThreadPool.Priority.MED);
        System.out.println(java.time.LocalTime.now());
        Assertions.assertNull(f1.get(3, TimeUnit.SECONDS));
        System.out.println("this line should be printed after 3 seconds");
        tp.shutdown();
        System.out.println(java.time.LocalTime.now());
        tp.awaitTermination(4, TimeUnit.SECONDS);
        System.out.println("this line should be printed after 7 seconds");
        System.out.println(java.time.LocalTime.now());
    }


}
