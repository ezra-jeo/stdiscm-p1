package ps1.shared;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.Map;
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

public class DivisorDivisionSchemeTest {
    private static final long TIMEOUT_SECONDS = 5;
    private static final Pattern PRIME = Pattern.compile("found prime number (\\d+)!");

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

    // A test harness redirects stdout so we can assert what the user sees.
    // Compare sets because worker scheduling does not guarantee output order.
    private static void checkSearch(int workers, int limit, Set<Integer> primes) throws Exception {
        for (boolean immediate : new boolean[] {true, false}) {
            PrintingScheme printer = new PrintingScheme(immediate);
            DivisorDivisionScheme scheme = new DivisorDivisionScheme(workers, limit, printer);

            DivisionScheme division = scheme;
            String duringSearch = capture(division::search);
            if (immediate) {
                checkPrimes(duringSearch, primes);
            } else {
                check(duringSearch.isEmpty(), "Buffered search printed before display");
                String displayed = capture(printer::displayBuffer);
                checkPrimes(displayed, primes);
                check(capture(printer::displayBuffer).equals(displayed),
                        "Displaying the buffer should preserve its contents");
            }
        }
    }

    // Reflection verifies exact allocation without exposing production internals.
    private static void checkAllocation(int candidate, int workerCount, int upperLimit) throws Exception {
        DivisorDivisionScheme scheme = new DivisorDivisionScheme(workerCount, candidate, new PrintingScheme(true));
        Method spawn = DivisorDivisionScheme.class.getDeclaredMethod("spawnWorkers", int.class);
        spawn.setAccessible(true);
        Map<?, ?> workers = (Map<?, ?>) spawn.invoke(scheme, candidate);
        check(workers.size() == workerCount, "Incorrect worker count");
        Field minField = DivisorDivisionJob.class.getDeclaredField("min");
        Field maxField = DivisorDivisionJob.class.getDeclaredField("max");
        Field candidateField = DivisorDivisionJob.class.getDeclaredField("candidate");
        minField.setAccessible(true);
        maxField.setAccessible(true);
        candidateField.setAccessible(true);
        Set<Integer> divisors = new HashSet<>();
        Set<Object> jobs = new HashSet<>();
        int smallest = Integer.MAX_VALUE;
        int largest = 0;
        for (Map.Entry<?, ?> entry : workers.entrySet()) {
            check(entry.getKey() instanceof Thread, "Missing thread");
            check(((Thread) entry.getKey()).getState() == Thread.State.NEW, "Factory started worker");
            check(entry.getValue() instanceof DivisorDivisionJob, "Missing job");
            Object job = entry.getValue();
            check(jobs.add(job), "Jobs shared across workers");
            check(candidateField.getInt(job) == candidate, "Wrong candidate passed to job");
            int min = minField.getInt(job);
            int max = maxField.getInt(job);
            int size = Math.max(0, max - min + 1);
            smallest = Math.min(smallest, size);
            largest = Math.max(largest, size);
            for (int divisor = min; divisor <= max; divisor++) {
                check(divisor >= 2 && divisor <= upperLimit, "Divisor out of bounds");
                check(divisors.add(divisor), "Overlapping divisor ranges");
            }
        }
        check(divisors.size() == upperLimit - 1, "Missing divisors");
        check(largest - smallest <= 1, "Unbalanced divisor ranges");
    }

    private static void checkJob(int min, int max, int candidate, boolean expected) throws Exception {
        DivisorDivisionJob job = new DivisorDivisionJob(min, max, candidate);
        check(!job.getFoundDivisor(), "Job must begin with no finding");
        Thread thread = new Thread(job);
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        check(!thread.isAlive(), "Job failed to finish");
        check(job.getFoundDivisor() == expected, "Incorrect divisor finding for " + candidate);
    }

    private static void checkAttribution() throws Exception {
        long coordinatorId = Thread.currentThread().threadId();
        PrintingScheme printer = new PrintingScheme(false);
        capture(() -> new DivisorDivisionScheme(3, 5, printer).search());
        String output = capture(printer::displayBuffer);
        String[] lines = output.strip().split("\\R");
        check(lines.length == 3, "Missing prime records");
        for (String line : lines) {
            check(line.startsWith("Thread " + coordinatorId + " "), "Prime not attributed to coordinator");
            int timestamp = line.indexOf(" Timestamp: ");
            check(timestamp >= 0, "Missing confirmation timestamp");
            LocalTime.parse(line.substring(timestamp + " Timestamp: ".length()));
        }
    }

    private static void invalidInputs() {
        boolean nullRejected = false;
        try {
            new DivisorDivisionScheme(2, 10, null);
        } catch (IllegalArgumentException expected) {
            nullRejected = true;
        }
        check(nullRejected, "Null printing scheme accepted");
        int[][] cases = {{0, 2}, {-1, 2}, {2, 0}, {2, -1}};
        for (int[] input : cases) {
            boolean rejected = false;
            try {
                new DivisorDivisionScheme(input[0], input[1], new PrintingScheme(true));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Invalid configuration accepted");
        }
    }

    private static void interruptedWaiting() throws Exception {
        // The worker cannot finish until the test explicitly releases it.
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
                DivisorDivisionScheme.awaitWorkers(Set.of(worker));
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

    public static void main(String[] args) throws Exception {
        invalidInputs();
        checkSearch(3, 1, Set.of());
        checkSearch(5, 2, Set.of(2));
        checkSearch(3, 5, Set.of(2, 3, 5));
        checkSearch(1, 10, Set.of(2, 3, 5, 7));
        checkSearch(4, 49, Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47));
        checkAllocation(2, 3, 1);
        checkAllocation(3, 3, 1);
        checkAllocation(5, 3, 2);
        checkAllocation(9, 4, 3);
        checkAllocation(49, 3, 7);
        checkAllocation(Integer.MAX_VALUE, 3, 46340);
        checkJob(2, 1, 2, false);
        checkJob(2, 2, 5, false);
        checkJob(2, 2, 6, true);
        checkJob(3, 3, 9, true);
        checkJob(2, 46340, Integer.MAX_VALUE, false);
        DivisionScheme reusable = new DivisorDivisionScheme(3, 10, new PrintingScheme(true));
        for (int run = 0; run < 10; run++) {
            checkPrimes(capture(reusable::search), Set.of(2, 3, 5, 7));
        }
        checkAttribution();
        interruptedWaiting();
        System.out.println("PASS: divisor validation, primes, squares, exact allocation, empty jobs, both modes, repeated runs, timestamps, coordinator attribution, interruption, int boundary");
    }
}

