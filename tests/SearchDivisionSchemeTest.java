import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchDivisionSchemeTest {
    private static final long TIMEOUT_SECONDS = 5;
    private static final Pattern PRIME = Pattern.compile("found prime number (\\d+)!");
    private static final Pattern RANGE = Pattern.compile("gets (\\d+) to (\\d+)");

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String capture(CheckedAction action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            action.run();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void checkPrimes(String output, Set<Integer> expected) {
        Matcher matcher = PRIME.matcher(output);
        List<Integer> actual = new ArrayList<>();
        while (matcher.find()) actual.add(Integer.parseInt(matcher.group(1)));
        check(new HashSet<>(actual).equals(expected), "Unexpected primes: " + actual);
        check(actual.size() == expected.size(), "Duplicate prime records: " + actual);
    }

    private static void checkSearch(int workers, int limit, Set<Integer> primes) throws Exception {
        String output = capture(() -> new SearchDivisionScheme(workers, limit).search());
        checkPrimes(output, primes);
        Matcher ranges = RANGE.matcher(output);
        Set<Integer> candidates = new HashSet<>();
        int rangeCount = 0;
        int smallest = Integer.MAX_VALUE;
        int largest = 0;
        while (ranges.find()) {
            rangeCount++;
            int min = Integer.parseInt(ranges.group(1));
            int max = Integer.parseInt(ranges.group(2));
            int size = Math.max(0, max - min + 1);
            smallest = Math.min(smallest, size);
            largest = Math.max(largest, size);
            for (int candidate = min; candidate <= max; candidate++) {
                check(candidate >= 2 && candidate <= limit, "Candidate outside search range");
                check(candidates.add(candidate), "Overlapping ranges at " + candidate);
            }
        }
        check(rangeCount == workers, "Incorrect worker count");
        check(candidates.size() == limit - 1, "Missing candidates");
        check(largest - smallest <= 1, "Unbalanced range sizes");
    }

    private static void invalidInputs() {
        int[][] cases = {{0, 2}, {-1, 2}, {2, 0}, {2, -1}};
        for (int[] input : cases) {
            boolean rejected = false;
            try {
                new SearchDivisionScheme(input[0], input[1]);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Invalid configuration accepted");
        }
    }

    private static void interruptedWaiting() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                release.await();
            } catch (InterruptedException error) {
                failure.set(error);
            }
        });
        worker.setDaemon(true);
        Thread coordinator = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                SearchDivisionScheme.awaitWorkers(List.of(worker));
                failure.set(new AssertionError("Interruption was not reported"));
            } catch (InterruptedException expected) {
                if (worker.isAlive()) failure.set(new AssertionError("Worker still alive when reported"));
            } finally {
                reported.countDown();
            }
        });
        coordinator.setDaemon(true);
        worker.start();
        coordinator.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (coordinator.getState() != Thread.State.WAITING
                    && reported.getCount() != 0 && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            check(coordinator.getState() == Thread.State.WAITING, "Coordinator did not retry waiting");
            check(reported.getCount() == 1, "Interruption reported before worker completed");
            coordinator.interrupt();
        } finally {
            release.countDown();
        }
        check(reported.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Join cleanup timed out");
        check(failure.get() == null, "Interruption failure: " + failure.get());
    }

    private static void maximumCandidate() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                new SearchJob(Integer.MAX_VALUE, Integer.MAX_VALUE).run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        worker.setDaemon(true);
        String output = capture(() -> {
            worker.start();
            worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            check(!worker.isAlive(), "Candidate counter overflowed or worker stalled");
        });
        check(failure.get() == null, "Maximum candidate failed: " + failure.get());
        checkPrimes(output, Set.of(Integer.MAX_VALUE));
    }

    public static void main(String[] args) throws Exception {
        invalidInputs();
        checkSearch(2, 1, Set.of());
        checkSearch(5, 2, Set.of(2));
        checkSearch(2, 7, Set.of(2, 3, 5, 7));
        checkSearch(2, 6, Set.of(2, 3, 5));
        checkSearch(3, 10, Set.of(2, 3, 5, 7));
        checkSearch(4, 49, Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47));
        checkPrimes(capture(() -> new SearchJob(3, 2).run()), Set.of());
        SearchDivisionScheme reusable = new SearchDivisionScheme(3, 10);
        for (int run = 0; run < 10; run++) {
            checkPrimes(capture(reusable::search), Set.of(2, 3, 5, 7));
        }
        interruptedWaiting();
        maximumCandidate();
        System.out.println("PASS: validation, primes, squares, coverage, empty workers, repeated searches, interruption, int boundary");
    }
}
