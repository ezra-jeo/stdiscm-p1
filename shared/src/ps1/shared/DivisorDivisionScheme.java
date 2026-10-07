package ps1.shared;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


class DivisorDivisionJob implements Runnable {
    private final int min;
    private final int max;
    private final int candidate;
    private boolean foundDivisor;

    DivisorDivisionJob(int min, int max, int candidate) {
        // Min and Max provided here should already be computed as a subset of 2 - sqrt(candidate)
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
    private final int workerCount;
    private final int searchLimit;
    private final PrintingScheme printingScheme;
    static final int MIN_CANDIDATE = 0;
    private static final int FIRST_PRIME_CANDIDATE = 2;


    public DivisorDivisionScheme(int workerCount, int searchLimit, PrintingScheme printingScheme) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("Worker count must be at least 1.");
        }
        if (searchLimit < MIN_CANDIDATE) {
            throw new IllegalArgumentException("Search limit must be at least " + MIN_CANDIDATE + ".");
        }
        if (printingScheme == null) {
            throw new IllegalArgumentException("Printing Scheme cannot be null.");
        }

        this.workerCount = workerCount;
        this.searchLimit = searchLimit;
        this.printingScheme = printingScheme;
    }

    private Map<Thread, DivisorDivisionJob> spawnWorkers(int candidate) {
        // Split 2 through sqrt(candidate). Keep jobs to read after joining.
        Map<Thread, DivisorDivisionJob> threadMap = new HashMap<>();

        int upperLimit = (int) Math.sqrt(candidate);
        int divisorCount = upperLimit - 1;// -1 so we don't count 1 as divisor and check of it.
        int countPerWorker = divisorCount / workerCount; // Floors automatically
        int remainder = divisorCount % workerCount;
        long nextMin = 2;

        for (int index = 0; index < workerCount; index++) {
            int count = countPerWorker + (index < remainder ? 1 : 0);
            long max = nextMin + count - 1;
            // Canonical empty endpoints avoid overflowing after the final int.
            int jobMin = count == 0 ? 2 : (int) nextMin;
            int jobMax = count == 0 ? 1 : (int) max;
            
            //System.out.println("Thread " + index + " gets " + jobMin + " to " + jobMax);
            DivisorDivisionJob divisorJob = new DivisorDivisionJob(jobMin, jobMax, candidate);
            Thread thread = new Thread(divisorJob);
            threadMap.put(thread, divisorJob);

            nextMin = max + 1;
        }

        return threadMap;
    }

    @Override 
    public void search() throws InterruptedException {
        // Candidates stay sequential; each gets a fresh batch of workers.
        for (long candidate = FIRST_PRIME_CANDIDATE; candidate <= searchLimit; candidate++) {
            Map<Thread, DivisorDivisionJob> workers = spawnWorkers((int) candidate);
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
                PrintingSchemeObject message = new PrintingSchemeObject(threadId, (int) candidate, currTime);
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

