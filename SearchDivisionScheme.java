import java.util.ArrayList;
import java.util.List;

class SearchJob implements Runnable {
    private final int min;
    private final int max;

    SearchJob(int min, int max) {
        this.min = min;
        this.max = max;
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
                System.out.println("Thread " + threadId
                        + " found prime number " + candidate + "!");
            }
        }
    }
}

public class SearchDivisionScheme {
    private final int workerCount;
    private final int searchLimit;

    public SearchDivisionScheme(int workerCount, int searchLimit) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("Worker count must be at least 1.");
        }
        if (searchLimit < 1) {
            throw new IllegalArgumentException("Search limit must be at least 1.");
        }
        this.workerCount = workerCount;
        this.searchLimit = searchLimit;
    }

    private List<Thread> spawnWorkers() {
        List<Thread> workers = new ArrayList<Thread>();
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
            System.out.println("Thread " + index + " gets " + jobMin + " to " + jobMax);
            workers.add(new Thread(new SearchJob(jobMin, jobMax)));
            nextMin = max + 1;
        }
        return workers;
    }

    public void search() throws InterruptedException {
        List<Thread> workers = spawnWorkers();
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
