package ps1.shared;

public interface DivisionScheme {
    /**
     * Searches using the configuration and printing object supplied at construction.
     * Returns normally only after all search workers have finished.
     *
     * @throws InterruptedException if the coordinator is interrupted; reported
     *         only after all started workers have finished
     */
    void search() throws InterruptedException;
}

