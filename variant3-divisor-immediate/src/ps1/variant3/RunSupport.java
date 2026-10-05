package ps1.variant3;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

public final class RunSupport {
    private static final int ERROR_EXIT_CODE = 1;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    private RunSupport() {
    }

    public static void run(String[] args, boolean divisorDivision, boolean immediate) {
        boolean started = false;
        boolean failed = false;
        long startNanos = 0;
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Usage: java -cp out <variant-main> [config-file]");
            }
            // Validate config before creating workers.
            Path configPath = Path.of(args.length == 0 ? "config.txt" : args[0]);
            SearchConfig config = SearchConfig.load(configPath);
            PrintingScheme printer = new PrintingScheme(immediate);
            DivisionScheme division = divisorDivision
                    ? new DivisorDivisionScheme(config.workerCount(), config.searchLimit(), printer)
                    : new SearchDivisionScheme(config.workerCount(), config.searchLimit(), printer);

            // Include search and final output in the elapsed duration.
            System.out.println("Start timestamp: " + LocalDateTime.now());
            startNanos = System.nanoTime();
            started = true;
            division.search();

            // Search returns only after all workers are joined.
            if (!immediate) {
                printer.displayBuffer();
            }
        } catch (IOException error) {
            System.err.println("Error: Cannot read config file: " + error.getMessage());
            failed = true;
        } catch (IllegalArgumentException error) {
            System.err.println("Error: " + error.getMessage());
            failed = true;
        } catch (InterruptedException error) {
            System.err.println("Error: Search interrupted. All started workers have finished.");
            Thread.currentThread().interrupt();
            failed = true;
        } finally {
            if (started) {
                System.out.println("End timestamp: " + LocalDateTime.now());
                double elapsedMillis = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND;
                System.out.printf("Elapsed milliseconds: %.3f%n", elapsedMillis);
            }
        }
        if (failed) {
            System.exit(ERROR_EXIT_CODE);
        }
    }
}



