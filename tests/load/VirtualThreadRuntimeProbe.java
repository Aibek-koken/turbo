import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualThreadRuntimeProbe {

    private VirtualThreadRuntimeProbe() {
    }

    public static void main(String[] args) throws Exception {
        int taskCount = args.length == 0 ? 16 : Integer.parseInt(args[0]);
        if (taskCount < 1 || taskCount > 128) {
            System.err.println("task count must be between 1 and 128");
            System.exit(2);
        }
        if (Runtime.version().feature() < 21) {
            System.err.println("Java 21 is required for the virtual-thread probe");
            System.exit(2);
        }

        AtomicInteger virtualThreads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        long startedNanos = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < taskCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (Thread.currentThread().isVirtual()) {
                        virtualThreads.incrementAndGet();
                    }
                    Thread.sleep(Duration.ofMillis(50));
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        boolean allVirtual = virtualThreads.get() == taskCount;
        System.out.println(
                "virtual_thread_probe "
                        + "java_feature=" + Runtime.version().feature()
                        + " tasks=" + taskCount
                        + " virtual_threads=" + virtualThreads.get()
                        + " elapsed_ms=" + elapsedMillis
                        + " all_virtual=" + allVirtual);

        if (!allVirtual) {
            System.err.println("Not every probe task ran on a virtual thread.");
            System.exit(1);
        }
    }
}
