package ps1.shared;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Signed64BoundaryTest {
    private static final long MAX = Long.MAX_VALUE;
    private static final long MAX_DIVISOR = 3037000499L;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid bounds accepted");
        } catch (IllegalArgumentException expected) {
            // Constructors must reject invalid values even without config loading.
        }
    }

    private static void constructorBounds() throws Exception {
        for (long invalid : new long[] {-1, 0}) {
            rejects(() -> new SearchConfig(invalid, 10));
            rejects(() -> new SearchDivisionScheme(invalid, 10, new PrintingScheme(true)));
            rejects(() -> new DivisorDivisionScheme(invalid, 10, new PrintingScheme(true)));
        }
        rejects(() -> new SearchConfig(1, -1));
        rejects(() -> new SearchDivisionScheme(1, -1, new PrintingScheme(true)));
        rejects(() -> new DivisorDivisionScheme(1, -1, new PrintingScheme(true)));
        new SearchConfig(MAX, MAX);
        new SearchDivisionScheme(1, MAX, new PrintingScheme(true));
        new DivisorDivisionScheme(1, MAX, new PrintingScheme(true));
        // Huge worker counts are harmless for an empty search: no allocation.
        for (long limit : new long[] {0, 1}) {
            new SearchDivisionScheme(MAX, limit, new PrintingScheme(true)).search();
            new DivisorDivisionScheme(MAX, limit, new PrintingScheme(true)).search();
        }
    }

    private static void divisorAllocation(long candidate, long expectedRoot) throws Exception {
        DivisorDivisionScheme scheme = new DivisorDivisionScheme(3, candidate, new PrintingScheme(true));
        Method spawn = DivisorDivisionScheme.class.getDeclaredMethod("spawnWorkers", long.class);
        spawn.setAccessible(true);
        Map<?, ?> workers = (Map<?, ?>) spawn.invoke(scheme, candidate);
        Field min = DivisorDivisionJob.class.getDeclaredField("min");
        Field max = DivisorDivisionJob.class.getDeclaredField("max");
        min.setAccessible(true);
        max.setAccessible(true);
        List<long[]> ranges = new ArrayList<>();
        for (Object job : workers.values()) ranges.add(new long[] {min.getLong(job), max.getLong(job)});
        ranges.sort(Comparator.comparingLong(range -> range[0]));
        check(ranges.size() == 3, "Wrong worker count");
        long next = 2;
        long smallest = Long.MAX_VALUE;
        long largest = 0;
        for (long[] range : ranges) {
            check(range[0] == next, "Gap or overlap in wide divisor ranges");
            long size = range[1] - range[0] + 1;
            smallest = Math.min(smallest, size);
            largest = Math.max(largest, size);
            next = range[1] + 1;
        }
        check(next == expectedRoot + 1, "Square root or final endpoint is wrong");
        check(largest - smallest <= 1, "Wide divisor ranges are unbalanced");
    }

    private static void highCandidateJobs() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                // Both are composite; this tests termination at the maximum cheaply.
                new SearchDivisionJob(MAX - 1, MAX, new PrintingScheme(true)).run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        worker.setDaemon(true);
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            worker.start();
            worker.join(TimeUnit.SECONDS.toMillis(5));
            check(!worker.isAlive(), "Maximum candidate loop did not terminate");
        } finally {
            System.setOut(original);
        }
        check(failure.get() == null, "High candidate job failed: " + failure.get());
        check(bytes.size() == 0, "Composite maximum candidates reported as primes");
        DivisorDivisionJob job = new DivisorDivisionJob(MAX_DIVISOR, MAX_DIVISOR, MAX_DIVISOR * MAX_DIVISOR);
        job.run();
        check(job.getFoundDivisor(), "Wide divisor was narrowed or miscomputed");
        DivisorDivisionJob empty = new DivisorDivisionJob(2, 1, MAX);
        empty.run();
        check(!empty.getFoundDivisor(), "Empty high-candidate job found a divisor");
    }

    private static void wideReports() {
        // Report formatting is independent of primality; use numeric boundary values.
        long belowMax = MAX - 1;
        PrintingScheme buffered = new PrintingScheme(false);
        LocalTime timestamp = LocalTime.NOON;
        buffered.report(new PrintingSchemeObject(1, MAX, timestamp));
        buffered.report(new PrintingSchemeObject(2, belowMax, timestamp));
        buffered.report(new PrintingSchemeObject(3, 2, timestamp));
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            buffered.displayBuffer();
            new PrintingScheme(true).report(new PrintingSchemeObject(7, MAX, timestamp));
        } finally {
            System.setOut(original);
        }
        check(bytes.toString(StandardCharsets.UTF_8).lines().toList().equals(List.of(
                "Found prime 2!", "Found prime 9223372036854775806!", "Found prime 9223372036854775807!",
                "Thread 7 found prime number 9223372036854775807! Timestamp: 12:00")),
                "Wide reports were narrowed, misformatted, or sorted incorrectly");
    }

    public static void main(String[] args) throws Exception {
        constructorBounds();
        divisorAllocation(MAX, MAX_DIVISOR);
        long square = MAX_DIVISOR * MAX_DIVISOR;
        divisorAllocation(square, MAX_DIVISOR);
        divisorAllocation(square - 1, MAX_DIVISOR - 1);
        highCandidateJobs();
        wideReports();
        System.out.println("PASS: signed 64-bit constructors, empty searches, exact roots, wide divisors, maximum-loop termination, report sorting");
    }
}
