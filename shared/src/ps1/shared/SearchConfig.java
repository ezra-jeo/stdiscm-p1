package ps1.shared;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record SearchConfig(BigInteger workerCount, BigInteger searchLimit) {
    public static final BigInteger MAX_UINT64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    public SearchConfig {
        validate(workerCount, BigInteger.ONE, "Worker count");
        validate(searchLimit, BigInteger.ZERO, "Search limit");
    }

    private static void validate(BigInteger value, BigInteger minimum, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " cannot be null.");
        }
        if (value.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(field + " must be at least " + minimum + ".");
        }
        if (value.compareTo(MAX_UINT64) > 0) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_UINT64 + ".");
        }
    }

    public static SearchConfig load(Path path) throws IOException {
        Map<String, BigInteger> values = new HashMap<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(path)) {
            lineNumber++;
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length != 2 || (!parts[0].equals("x") && !parts[0].equals("y"))) {
                throw new IllegalArgumentException("Config line " + lineNumber + " must be x <integer> or y <integer>.");
            }
            if (values.containsKey(parts[0])) {
                throw new IllegalArgumentException("Duplicate config value: " + parts[0] + ".");
            }
            values.put(parts[0], parseInteger(parts[0], parts[1], lineNumber));
        }
        if (!values.containsKey("x") || !values.containsKey("y")) {
            throw new IllegalArgumentException("Config must contain both x and y.");
        }
        return new SearchConfig(values.get("x"), values.get("y"));
    }

    private static BigInteger parseInteger(String key, String value, int lineNumber) {
        // Check written syntax first. Never round or coerce decimals.
        if (!value.matches("[+-]?[0-9]+")) {
            throw new IllegalArgumentException("Config " + key + " on line " + lineNumber
                    + " must be an integer; decimals, text, and other number formats are not allowed.");
        }
        BigInteger number = new BigInteger(value);
        if (number.compareTo(MAX_UINT64) > 0) {
            throw new IllegalArgumentException("Config " + key + " on line " + lineNumber
                    + " is outside the uint64 range (0 to " + MAX_UINT64 + ").");
        }
        // Negative values get the same field-specific minimum errors as before.
        return number;
    }
}
