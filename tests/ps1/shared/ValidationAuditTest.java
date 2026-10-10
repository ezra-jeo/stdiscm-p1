package ps1.shared;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ValidationAuditTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final List<String> evidence = new ArrayList<>();

    private record ConfigCase(String name, String contents, String error, long x, long y) {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void addInvalid(List<ConfigCase> cases, String key, String value, String error) {
        String other = key.equals("x") ? "y 10\n" : "x 2\n";
        cases.add(new ConfigCase(key + "=" + value, other + key + " " + value + "\n", error, 0, 0));
    }

    private static List<ConfigCase> cases() {
        List<ConfigCase> cases = new ArrayList<>();
        cases.add(new ConfigCase("minimum", "x 1\ny 0\n", null, 1, 0));
        cases.add(new ConfigCase("one", "x 1\ny 1\n", null, 1, 1));
        cases.add(new ConfigCase("first prime", "x 1\ny 2\n", null, 1, 2));
        cases.add(new ConfigCase("more workers than candidates", "x 5\ny 2\n", null, 5, 2));
        cases.add(new ConfigCase("signs and zeros", "x +0002\ny +0010\n", null, 2, 10));
        cases.add(new ConfigCase("negative zero", "x 2\ny -0\n", null, 2, 0));
        cases.add(new ConfigCase("reversed keys and tabs", "\n y\t10\n\tx  2\n", null, 2, 10));
        cases.add(new ConfigCase("CRLF", "x 2\r\ny 10\r\n", null, 2, 10));
        cases.add(new ConfigCase("no final newline", "x 2\ny 10", null, 2, 10));
        cases.add(new ConfigCase("maximum int", "x 2147483647\ny 2147483647\n", null, Integer.MAX_VALUE, Integer.MAX_VALUE));
        cases.add(new ConfigCase("below maximum int", "x 2147483646\ny 2147483646\n", null, Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1));
        for (String value : new String[] {"2147483648", "4294967295", "9223372036854775807",
                "9223372036854775806"}) {
            long number = Long.parseLong(value);
            cases.add(new ConfigCase("wide integer " + value, "x " + value + "\ny " + value + "\n", null, number, number));
        }
        String[] nonIntegers = {"hello", "true", "false", "null", "2.0", "3.5", "-3.5", ".5", "5.",
                "1e3", "1E+3", "0x10", "0b10", "1_000", "1,000", "+", "-", "++2", "--2", "+-2", "-+2",
                "NaN", "Infinity", "-Infinity", "[]", "[1,2]", "{}", "\"2\"", "'2'", "2L", "2f",
                "\u0662", "\uff12", "\u22122", "2\u0000", "2\u00a0"};
        for (String key : new String[] {"x", "y"}) {
            for (String value : nonIntegers) addInvalid(cases, key, value, "must be an integer");
            for (String value : new String[] {"9223372036854775808", "18446744073709551615",
                    "999999999999999999999999999999999999999"}) {
                addInvalid(cases, key, value, "outside the signed 64-bit range");
            }
            addInvalid(cases, key, "-9223372036854775809", "outside the signed 64-bit range");
            String[] negative = key.equals("x") ? new String[] {"0", "-0", "-1", "-2147483648", "-2147483649", "-9223372036854775808"}
                    : new String[] {"-1", "-2147483648", "-2147483649", "-9223372036854775808"};
            for (String value : negative) addInvalid(cases, key, value, "must be at least " + (key.equals("x") ? "1" : "0"));
        }
        String[][] structure = {
            {"empty", "", "must contain both x and y"},
            {"blank", " \t\r\n", "must contain both x and y"},
            {"missing x", "y 10\n", "must contain both x and y"},
            {"missing y", "x 2\n", "must contain both x and y"},
            {"duplicate x", "x 2\nx 2\ny 10\n", "Duplicate config value: x"},
            {"duplicate y", "x 2\ny 10\ny 11\n", "Duplicate config value: y"},
            {"unknown key", "x 2\ny 10\nz 1\n", "must be x <integer> or y <integer>"},
            {"uppercase key", "X 2\ny 10\n", "must be x <integer> or y <integer>"},
            {"equals separator", "x=2\ny=10\n", "must be x <integer> or y <integer>"},
            {"colon separator", "x: 2\ny 10\n", "must be x <integer> or y <integer>"},
            {"missing value", "x\ny 10\n", "must be x <integer> or y <integer>"},
            {"extra token", "x 2 extra\ny 10\n", "must be x <integer> or y <integer>"},
            {"same line", "x 2 y 10\n", "must be x <integer> or y <integer>"},
            {"inline comment", "x 2 # workers\ny 10\n", "must be x <integer> or y <integer>"},
            {"comment line", "# config\nx 2\ny 10\n", "must be x <integer> or y <integer>"},
            {"UTF-8 BOM", "\ufeffx 2\ny 10\n", "must be x <integer> or y <integer>"},
            {"nonbreaking separator", "x\u00a02\ny 10\n", "must be x <integer> or y <integer>"}
        };
        for (String[] row : structure) cases.add(new ConfigCase(row[0], row[1], row[2], 0, 0));
        return cases;
    }

    private static void record(String layer, String name, String result) {
        evidence.add(csv(layer) + "," + csv(name) + "," + csv(result));
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static void checkLoad(Path config, ConfigCase test) throws Exception {
        Files.writeString(config, test.contents());
        try {
            SearchConfig loaded = SearchConfig.load(config);
            check(test.error() == null, "Invalid input accepted: " + test.name());
            check(loaded.workerCount() == test.x() && loaded.searchLimit() == test.y(), "Input changed: " + test.name());
        } catch (IllegalArgumentException error) {
            check(test.error() != null && error.getMessage().contains(test.error()), "Wrong rejection: " + test.name() + ": " + error);
        }
        record("loader", test.name(), "PASS");
    }

    private static void checkProcess(String classPath, Path directory, String name, String expectedError,
            String... arguments) throws Exception {
        String variant = Path.of(classPath).getFileName().toString().split("-")[0];
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classPath, "ps1." + variant + ".Main"));
        command.addAll(List.of(arguments));
        Path stdout = directory.resolve("stdout.txt");
        Path stderr = directory.resolve("stderr.txt");
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new AssertionError("CLI timed out: " + name);
        }
        String output = Files.readString(stdout);
        String errors = Files.readString(stderr);
        if (expectedError == null) {
            check(process.exitValue() == 0 && errors.isEmpty(), "Valid CLI failed: " + name + ": " + errors);
            check(output.contains("Start timestamp:") && output.contains("End timestamp:"), "Missing timestamps: " + name);
        } else {
            check(process.exitValue() == 1, "Wrong error exit code: " + name);
            check(errors.contains(expectedError), "Wrong CLI error: " + name + ": " + errors);
            check(output.isEmpty(), "Rejected input started a run: " + name);
        }
        record(variant, name, "PASS");
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "Provide evidence path and four variant classpaths");
        Path report = Path.of(args[0]);
        Path directory = Files.createTempDirectory("ps1-validation-audit-");
        Path config = directory.resolve("config.txt");
        evidence.add("layer,case,result");
        try {
            List<ConfigCase> cases = cases();
            for (ConfigCase test : cases) checkLoad(config, test);
            for (Path invalidPath : List.of(directory.resolve("missing.txt"), directory)) {
                try {
                    SearchConfig.load(invalidPath);
                    throw new AssertionError("Invalid path accepted");
                } catch (IOException expected) {
                    record("loader", invalidPath.equals(directory) ? "directory path" : "missing path", "PASS");
                }
            }
            Files.write(config, new byte[] {(byte) 0xc3, (byte) 0x28});
            try {
                SearchConfig.load(config);
                throw new AssertionError("Invalid UTF-8 accepted");
            } catch (IOException expected) {
                record("loader", "invalid UTF-8", "PASS");
            }
            for (int index = 1; index < args.length; index++) {
                String classPath = args[index];
                for (ConfigCase test : cases) {
                    // Maximum values are representation tests, not resource tests.
                    if (test.error() == null && (test.x() > 5 || test.y() > 10)) continue;
                    Files.writeString(config, test.contents());
                    checkProcess(classPath, directory, test.name(), test.error(), config.toString());
                }
                Files.writeString(config, "x 2\ny 0\n");
                checkProcess(classPath, directory, "default config", null);
                checkProcess(classPath, directory, "extra argument", "Usage:", config.toString(), "extra");
                checkProcess(classPath, directory, "missing file", "Cannot read config file", directory.resolve("missing.txt").toString());
                checkProcess(classPath, directory, "directory path", "Cannot read config file", directory.toString());
                checkProcess(classPath, directory, "empty path", "Cannot read config file", "");
                Files.write(config, new byte[] {(byte) 0xc3, (byte) 0x28});
                checkProcess(classPath, directory, "invalid UTF-8", "Cannot read config file", config.toString());
                System.out.println("PASS: exhaustive validation categories for variant " + index);
            }
            System.out.println("PASS: " + (evidence.size() - 1) + " validation checks; evidence: " + report);
        } finally {
            Files.createDirectories(report.toAbsolutePath().getParent());
            Files.write(report, evidence, StandardCharsets.UTF_8);
            for (String file : List.of("config.txt", "stdout.txt", "stderr.txt")) Files.deleteIfExists(directory.resolve(file));
            Files.deleteIfExists(directory);
        }
    }
}
