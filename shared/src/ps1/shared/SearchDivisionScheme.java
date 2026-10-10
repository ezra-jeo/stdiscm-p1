package ps1.shared;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;

class SearchDivisionJob implements Runnable {
    private final BigInteger min;
    private final BigInteger max;
    private final PrintingScheme printingScheme;

    SearchDivisionJob(BigInteger min, BigInteger max, PrintingScheme printingScheme) {
        this.min = min;
        this.max = max;
        this.printingScheme = printingScheme; // Validated in composing class.
    }

    @Override
    public void run() {
        // BigInteger can step past uint64 max without wrapping back to zero.
        for (BigInteger candidate = min; candidate.compareTo(max) <= 0;
                candidate = candidate.add(BigInteger.ONE)) {
            long upperLimit = candidate.sqrt().longValueExact();
            boolean prime = candidate.compareTo(BigInteger.TWO) >= 0;
            for (long divisor = 2; divisor <= upperLimit; divisor++) {
                if (candidate.mod(BigInteger.valueOf(divisor)).signum() == 0) {
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
    private final BigInteger workerCount;
    private final BigInteger searchLimit;
    private final PrintingScheme printingScheme;

    public SearchDivisionScheme(BigInteger workerCount, BigInteger searchLimit, PrintingScheme printingScheme) {
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
        BigInteger candidateCount = searchLimit.subtract(BigInteger.ONE);
        BigInteger[] division = candidateCount.divideAndRemainder(workerCount);
        BigInteger nextMin = BigInteger.TWO;

        for (BigInteger index = BigInteger.ZERO; index.compareTo(workerCount) < 0;
                index = index.add(BigInteger.ONE)) {
            BigInteger count = division[0].add(index.compareTo(division[1]) < 0
                    ? BigInteger.ONE : BigInteger.ZERO);
            BigInteger max = nextMin.add(count).subtract(BigInteger.ONE);
            BigInteger jobMin = count.signum() == 0 ? BigInteger.TWO : nextMin;
            BigInteger jobMax = count.signum() == 0 ? BigInteger.ONE : max;
            workers.add(new Thread(new SearchDivisionJob(jobMin, jobMax, this.printingScheme)));
            nextMin = max.add(BigInteger.ONE);
        }
        return workers;
    }

    @Override
    public void search() throws InterruptedException {
        if (searchLimit.compareTo(BigInteger.TWO) < 0) {
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

