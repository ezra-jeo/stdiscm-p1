package ps1.shared;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class DivisorDivisionJob implements Runnable {
    private final long min;
    private final long max;
    private final long candidate;
    private boolean foundDivisor;

    DivisorDivisionJob(long min, long max, long candidate) {
        // Divisors stop at sqrt(Long.MAX_VALUE), which fits safely in a long.
        this.min = min;
        this.max = max;
        this.candidate = candidate;
        this.foundDivisor = false;
    }

    @Override
    public void run() {
        for (long divisor = min; divisor <= max; divisor++) {
            if (candidate % divisor == 0) {
                this.foundDivisor = true;
                return;
            }
        }
    }

    public boolean getFoundDivisor() {
        return foundDivisor;
    }
}

public class DivisorDivisionScheme implements DivisionScheme {
    private final long workerCount;
    private final long searchLimit;
    private final PrintingScheme printingScheme;

    public DivisorDivisionScheme(long workerCount, long searchLimit, PrintingScheme printingScheme) {
        new SearchConfig(workerCount, searchLimit); // Same bounds as config loading.
        if (printingScheme == null) {
            throw new IllegalArgumentException("Printing Scheme cannot be null.");
        }
        this.workerCount = workerCount;
        this.searchLimit = searchLimit;
        this.printingScheme = printingScheme;
    }

    private Map<Thread, DivisorDivisionJob> spawnWorkers(long candidate) {
        // Split 2 through sqrt(candidate). Keep jobs to read after joining.
        Map<Thread, DivisorDivisionJob> threadMap = new HashMap<>();
        long upperLimit = floorSqrt(candidate);
        long divisorCount = upperLimit - 1;
        long countPerWorker = divisorCount / workerCount;
        long remainder = divisorCount % workerCount;
        long nextMin = 2;

        for (long index = 0; index < workerCount; index++) {
            long count = countPerWorker + (index < remainder ? 1 : 0);
            long max = nextMin + count - 1;
            long jobMin = count == 0 ? 2 : nextMin;
            long jobMax = count == 0 ? 1 : max;
            DivisorDivisionJob divisorJob = new DivisorDivisionJob(jobMin, jobMax, candidate);
            Thread thread = new Thread(divisorJob);
            threadMap.put(thread, divisorJob);
            nextMin = max + 1;
        }
        return threadMap;
    }

    static long floorSqrt(long candidate) {
        long root = (long) Math.sqrt(candidate);
        // Correct floating-point rounding without overflowing root * root.
        while (root > candidate / root) root--;
        while (root + 1 <= candidate / (root + 1)) root++;
        return root;
    }

    @Override 
    public void search() throws InterruptedException {
        // Candidates stay sequential; each gets a fresh batch of workers.
        for (long offset = 1; offset < searchLimit; offset++) {
            long candidate = offset + 1;
            Map<Thread, DivisorDivisionJob> workers = spawnWorkers(candidate);
            try {
                for (Thread worker: workers.keySet()) {
                    worker.start();
                }
            } catch (RuntimeException | Error startFailure) {
            // Finish any workers started before a later start failed.
                try {
                    awaitWorkers(workers.keySet());
                } catch (InterruptedException interruption) {
                    startFailure.addSuppressed(interruption);
                    Thread.currentThread().interrupt();
                }
                throw startFailure;
            }
            awaitWorkers(workers.keySet());

            // Any found divisor makes this candidate composite.
            boolean prime = true;

            for (DivisorDivisionJob job: workers.values()) {
                if (job.getFoundDivisor()) {
                    prime = false;
                    break;
                }
            }

            if (prime) {
                long threadId = Thread.currentThread().threadId();
                LocalTime currTime = LocalTime.now();
                PrintingSchemeObject message = new PrintingSchemeObject(threadId, candidate, currTime);
                printingScheme.report(message);
            }
        }
    }

    static void awaitWorkers(Set<Thread> workers) throws InterruptedException {
        InterruptedException firstInterruption = null;
        for (Thread worker : workers) {
            boolean joined = false;
            while (!joined) {
                try {
                    worker.join();
                    joined = true;
                } catch (InterruptedException interruption) {
                    if (firstInterruption == null) {
                        firstInterruption = interruption;
                    }
                    // Restoring the flag here would interrupt subsequent waits.
                }
            }
        }
        // A join on an already-dead thread may not observe an interrupt.
        boolean pendingInterruption = Thread.interrupted();
        if (firstInterruption != null) {
            throw firstInterruption;
        }
        if (pendingInterruption) {
            throw new InterruptedException("Search coordinator was interrupted.");
        }
    }
}

