package ps1.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariantEntryPointTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final Pattern PRIME = Pattern.compile("(?:found prime number |Found prime )(\\d+)!");

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void runCase(String classPath, Path config, Set<Integer> expected,
            String expectedError) throws Exception {
        Path outputPath = Files.createTempFile("ps1-output-", ".txt");
        try {
            // A separate JVM lets us check Main's exit code without exiting tests.
            String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String variant = Path.of(classPath).getFileName().toString().split("-")[0];
            String mainClass = "ps1." + variant + ".Main";
            Process process = new ProcessBuilder(javaCommand, "-cp", classPath, mainClass, config.toString())
                    .redirectErrorStream(true).redirectOutput(outputPath.toFile()).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new AssertionError("Variant timed out: " + classPath);
            }
            String output = Files.readString(outputPath);
            if (expectedError != null) {
                check(process.exitValue() != 0, "Invalid config returned success");
                check(output.contains(expectedError), "Wrong error: " + output);
                check(!output.contains("Start timestamp:"), "Invalid config started search");
                return;
            }
            check(process.exitValue() == 0, "Valid run failed: " + output);
            Matcher records = PRIME.matcher(output);
            Set<Integer> actual = new HashSet<>();
            int start = output.indexOf("Start timestamp:");
            int end = output.indexOf("End timestamp:");
            check(start >= 0 && end > start, "Missing or reversed timestamps");
            while (records.find()) {
                check(records.start() > start && records.start() < end, "Record outside run timestamps");
                check(actual.add(Integer.parseInt(records.group(1))), "Duplicate prime");
            }
            check(actual.equals(expected), "Wrong primes: " + actual);
            if (variant.equals("variant2") || variant.equals("variant4")) {
                String primeOutput = output.substring(output.indexOf("\n", start) + 1, end);
                check(primeOutput.lines().toList().equals(expected.stream().sorted()
                        .map(prime -> "Found prime " + prime + "!").toList()),
                        "Buffered records are not sorted prime-only lines");
            }
            check(output.contains("Elapsed milliseconds:"), "Missing elapsed duration");
        } finally {
            Files.deleteIfExists(outputPath);
        }
    }

    public static void main(String[] classPaths) throws Exception {
        check(classPaths.length == 4, "Provide all four compiled variant directories");
        Path directory = Files.createTempDirectory("ps1-config-tests-");
        Path config = directory.resolve("config.txt");
        String[][] invalid = {
            {"x 0\ny 10\n", "Worker count must be at least 1."},
            {"x -1\ny 10\n", "Worker count must be at least 1."},
            {"x 2\ny -1\n", "Search limit must be at least 0."},
            {"x two\ny 10\n", "must be an integer"},
            {"x 2\ny 18446744073709551616\n", "outside the uint64 range"},
            {"x 2.5\ny 10\n", "must be an integer"},
            {"x false\ny 10\n", "must be an integer"},
            {"x 2\ny null\n", "must be an integer"},
            {"x 2\ny 3.0\n", "must be an integer"},
            {"x 2\ny 1e3\n", "must be an integer"},
            {"x 18446744073709551616\ny 10\n", "outside the uint64 range"},
            {"x 2\n", "must contain both x and y"},
            {"", "must contain both x and y"},
            {"x 2\nx 3\ny 10\n", "Duplicate config value"},
            {"z 2\ny 10\n", "must be x <integer> or y <integer>"},
            {"x 2 extra\ny 10\n", "must be x <integer> or y <integer>"}
        };
        try {
            for (String classPath : classPaths) {
                Files.writeString(config, "x 3\ny 10\n");
                runCase(classPath, config, Set.of(2, 3, 5, 7), null);
                Files.writeString(config, "x 5\ny 2\n");
                runCase(classPath, config, Set.of(2), null);
                Files.writeString(config, "x 3\ny 0\n");
                runCase(classPath, config, Set.of(), null);
                Files.writeString(config, "x 3\ny 1\n");
                runCase(classPath, config, Set.of(), null);
                Files.writeString(config, "x 18446744073709551615\ny 0\n");
                runCase(classPath, config, Set.of(), null);
                Files.writeString(config, "x 18446744073709551615\ny 1\n");
                runCase(classPath, config, Set.of(), null);
                for (String[] invalidCase : invalid) {
                    Files.writeString(config, invalidCase[0]);
                    runCase(classPath, config, Set.of(), invalidCase[1]);
                }
                runCase(classPath, directory.resolve("missing.txt"), Set.of(), "Cannot read config file");
                runCase(classPath, directory, Set.of(), "Cannot read config file");
            }
        } finally {
            Files.deleteIfExists(config);
            Files.deleteIfExists(directory);
        }
        System.out.println("PASS: all four entry points, run timestamps, output, empty searches, config errors, exit codes");
    }
}


