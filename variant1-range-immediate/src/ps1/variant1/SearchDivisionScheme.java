package ps1.variant1;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;

class SearchDivisionJob implements Runnable {
    private final int min;
    private final int max;
    private final PrintingScheme printingScheme;

    SearchDivisionJob(int min, int max, PrintingScheme printingScheme) {
        this.min = min;
        this.max = max;
        this.printingScheme = printingScheme; // Validated in composing class.
    }

    @Override
    public void run() {
        long threadId = Thread.currentThread().threadId();
        // Prevent the counter from wrapping after Integer.MAX_VALUE.
        for (long candidate = min; candidate <= max; candidate++) {
            int upperLimit = (int) Math.sqrt(candidate);
            boolean prime = candidate >= 2;
            for (int divisor = 2; divisor <= upperLimit; divisor++) {
                if (candidate % divisor == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) {
                LocalTime currTime = LocalTime.now();
                printingScheme.report("Thread " + threadId 
                    + " found prime number " + candidate + "!"
                    + " Timestamp: " + currTime);
            }
        }
    }
}

public class SearchDivisionScheme implements DivisionScheme {
    private final int workerCount;
    private final int searchLimit;
    private final PrintingScheme printingScheme;

    public SearchDivisionScheme(int workerCount, int searchLimit, PrintingScheme printingScheme) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("Worker count must be at least 1.");
        }
        if (searchLimit < 1) {
            throw new IllegalArgumentException("Search limit must be at least 1.");
        }
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
        int candidateCount = searchLimit - 1;
        int countPerWorker = candidateCount / workerCount;
        int remainder = candidateCount % workerCount;
        long nextMin = 2;
        
        for (int index = 0; index < workerCount; index++) {
            int count = countPerWorker + (index < remainder ? 1 : 0);
            long max = nextMin + count - 1;
            // Canonical empty endpoints avoid overflowing after the final int.
            int jobMin = count == 0 ? 2 : (int) nextMin;
            int jobMax = count == 0 ? 1 : (int) max;
            
            //System.out.println("Thread " + index + " gets " + jobMin + " to " + jobMax);
            
            workers.add(new Thread(new SearchDivisionJob(jobMin, jobMax, this.printingScheme)));
            nextMin = max + 1;
        }
        return workers;
    }

    @Override
    public void search() throws InterruptedException {
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


