package ps1.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record SearchConfig(int workerCount, int searchLimit) {
    public SearchConfig {
        if (workerCount < 1) {
            throw new IllegalArgumentException("Worker count must be at least 1.");
        }
        if (searchLimit < 1) {
            throw new IllegalArgumentException("Search limit must be at least 1.");
        }
    }

    public static SearchConfig load(Path path) throws IOException {
        Map<String, Integer> values = new HashMap<>();
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
            try {
                values.put(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException invalidNumber) {
                throw new IllegalArgumentException("Config " + parts[0] + " must be a 32-bit integer.", invalidNumber);
            }
        }
        if (!values.containsKey("x") || !values.containsKey("y")) {
            throw new IllegalArgumentException("Config must contain both x and y.");
        }
        return new SearchConfig(values.get("x"), values.get("y"));
    }
}

