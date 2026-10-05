package ps1.shared;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
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
            SearchDivisionScheme scheme = new SearchDivisionScheme(workers, limit, printer);
            checkWorkerCreation(scheme, workers);
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

    // Worker count is an internal contract. Reflection keeps the production
    // helper private and avoids depending on temporary range debug prints.
    // This checks creation, not exact coverage of composite candidates.
    private static void checkWorkerCreation(SearchDivisionScheme scheme, int expected) throws Exception {
        Method spawn = SearchDivisionScheme.class.getDeclaredMethod("spawnWorkers");
        spawn.setAccessible(true);
        Object result = spawn.invoke(scheme);
        check(result instanceof List<?>, "Worker factory must return a list");
        List<?> workers = (List<?>) result;
        check(workers.size() == expected, "Incorrect worker count");
        for (Object worker : workers) {
            check(worker instanceof Thread, "Worker is not a Thread");
            check(((Thread) worker).getState() == Thread.State.NEW,
                    "Worker factory started a thread prematurely");
        }
    }

    private static void invalidInputs() {
        boolean nullRejected = false;
        try {
            new SearchDivisionScheme(2, 10, null);
        } catch (IllegalArgumentException expected) {
            nullRejected = true;
        }
        check(nullRejected, "Null printing scheme accepted");
        int[][] cases = {{0, 2}, {-1, 2}, {2, 0}, {2, -1}};
        for (int[] input : cases) {
            boolean rejected = false;
            try {
                new SearchDivisionScheme(input[0], input[1], new PrintingScheme(true));
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
                new SearchDivisionJob(Integer.MAX_VALUE, Integer.MAX_VALUE, new PrintingScheme(true)).run();
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
        checkPrimes(capture(() -> new SearchDivisionJob(3, 2, new PrintingScheme(true)).run()), Set.of());
        DivisionScheme reusable = new SearchDivisionScheme(3, 10, new PrintingScheme(true));
        for (int run = 0; run < 10; run++) {
            checkPrimes(capture(reusable::search), Set.of(2, 3, 5, 7));
        }
        interruptedWaiting();
        maximumCandidate();
        System.out.println("PASS: validation, primes, squares, worker creation, both printing modes, repeated display, empty jobs, repeated searches, interruption, int boundary");
    }
}

