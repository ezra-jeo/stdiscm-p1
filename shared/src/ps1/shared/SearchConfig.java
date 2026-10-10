package ps1.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record SearchConfig(long workerCount, long searchLimit) {
    public static final long MAX_VALUE = Long.MAX_VALUE;

    public SearchConfig {
        validate(workerCount, 1, "Worker count");
        validate(searchLimit, 0, "Search limit");
    }

    private static void validate(long value, long minimum, String field) {
        if (value < minimum) {
            throw new IllegalArgumentException(field + " must be at least " + minimum + ".");
        }
    }

    public static SearchConfig load(Path path) throws IOException {
        Map<String, Long> values = new HashMap<>();
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

    private static long parseInteger(String key, String value, int lineNumber) {
        // Check written syntax first. Never round or coerce decimals.
        if (!value.matches("[+-]?[0-9]+")) {
            throw new IllegalArgumentException("Config " + key + " on line " + lineNumber
                    + " must be an integer; decimals, text, and other number formats are not allowed.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Config " + key + " on line " + lineNumber
                    + " is outside the signed 64-bit range (" + Long.MIN_VALUE + " to " + MAX_VALUE + ").", error);
        }
    }
}
