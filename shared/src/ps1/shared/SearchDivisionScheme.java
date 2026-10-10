package ps1.shared;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;

class SearchDivisionJob implements Runnable {
    private final long min;
    private final long max;
    private final PrintingScheme printingScheme;

    SearchDivisionJob(long min, long max, PrintingScheme printingScheme) {
        this.min = min;
        this.max = max;
        this.printingScheme = printingScheme; // Validated in composing class.
    }

    @Override
    public void run() {
        // The offset can reach max without stepping past Long.MAX_VALUE.
        for (long offset = min - 1; offset < max; offset++) {
            long candidate = offset + 1;
            boolean prime = candidate >= 2;
            for (long divisor = 2; divisor <= candidate / divisor; divisor++) {
                if (candidate % divisor == 0) {
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
}

public class SearchDivisionScheme implements DivisionScheme {
    private final long workerCount;
    private final long searchLimit;
    private final PrintingScheme printingScheme;

    public SearchDivisionScheme(long workerCount, long searchLimit, PrintingScheme printingScheme) {
        new SearchConfig(workerCount, searchLimit); // Same bounds as config loading.
        if (printingScheme == null) {
            throw new IllegalArgumentException("Printing Scheme cannot be null.");
        }
        this.workerCount = workerCount;
        this.searchLimit = searchLimit;
        this.printingScheme = printingScheme;
    }

    private List<Thread> spawnWorkers() {
        // Split candidates evenly. First workers get the remainder.
        List<Thread> workers = new ArrayList<>();
        long candidateCount = searchLimit - 1;
        long countPerWorker = candidateCount / workerCount;
        long remainder = candidateCount % workerCount;
        long nextMin = 2;

        for (long index = 0; index < workerCount; index++) {
            long count = countPerWorker + (index < remainder ? 1 : 0);
            long max = nextMin + (count - 1);
            long jobMin = count == 0 ? 2 : nextMin;
            long jobMax = count == 0 ? 1 : max;
            workers.add(new Thread(new SearchDivisionJob(jobMin, jobMax, this.printingScheme)));
            // Empty workers need no next endpoint; max itself may be Long.MAX_VALUE.
            if (count > 0 && max < searchLimit) {
                nextMin = max + 1;
            }
        }
        return workers;
    }

    @Override
    public void search() throws InterruptedException {
        if (searchLimit < 2) {
           return; // Skips 0 and 1 since they are non primes regardless
        }

        List<Thread> workers = spawnWorkers();
        // Start all workers before waiting on any one worker.
        try {
            for (Thread worker : workers) {
                worker.start();
            }
        } catch (RuntimeException | Error startFailure) {
            // Finish any workers started before a later start failed.
            try {
                awaitWorkers(workers);
            } catch (InterruptedException interruption) {
                startFailure.addSuppressed(interruption);
                Thread.currentThread().interrupt();
            }
            throw startFailure;
        }
        awaitWorkers(workers);
    }

    static void awaitWorkers(List<Thread> workers) throws InterruptedException {
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

