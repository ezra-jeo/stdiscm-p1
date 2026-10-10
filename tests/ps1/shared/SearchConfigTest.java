package ps1.shared;

import java.math.BigInteger;

import java.nio.file.Files;
import java.nio.file.Path;

public class SearchConfigTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void reject(Path config, String contents, String expectedError) throws Exception {
        Files.writeString(config, contents);
        try {
            SearchConfig.load(config);
            throw new AssertionError("Invalid config accepted: " + contents);
        } catch (IllegalArgumentException error) {
            check(error.getMessage().contains(expectedError), "Wrong validation error: " + error.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        Path config = Files.createTempFile("ps1-config-validation-", ".txt");
        try {
            // Check parsing separately; do not start billions of workers.
            Files.writeString(config, "x 18446744073709551615\ny 18446744073709551615\n");
            SearchConfig maximum = SearchConfig.load(config);
            check(maximum.workerCount().equals(SearchConfig.MAX_UINT64), "Maximum x rejected or changed");
            check(maximum.searchLimit().equals(SearchConfig.MAX_UINT64), "Maximum y rejected or changed");

            Files.writeString(config, "\ny\t1\nx +0003\n\n");
            SearchConfig small = SearchConfig.load(config);
            check(small.workerCount().equals(BigInteger.valueOf(3)) && small.searchLimit().equals(BigInteger.ONE), "Whitespace/sign parsing failed");

            Files.writeString(config, "x 3\ny 0\n");
            check(SearchConfig.load(config).searchLimit().equals(BigInteger.ZERO), "Zero search limit rejected");

            // Config values are text. Reject formats that are not decimal integers
            // for both x and y, even when another language accepts them as numbers.
            String[] wrongTypes = {
                    "two", "true", "false", "null", "2.0", "-2.5", "1e3",
                    "0x10", "0b10", "1_000", "1,000", "+", "-", "++2", "+-2",
                    "NaN", "Infinity", "-Infinity", "[]", "[1,2]", "{}", "\"2\""
            };
            String[] overflowValues = {"18446744073709551616", "18446744073709551617",
                    "999999999999999999999999999999999999999"};
            for (String key : new String[] {"x", "y"}) {
                String other = key.equals("x") ? "y 10\n" : "x 3\n";
                for (String value : wrongTypes) {
                    reject(config, other + key + " " + value + "\n", "Config " + key + " on line 2 must be an integer");
                }
                for (String value : overflowValues) {
                    reject(config, other + key + " " + value + "\n", "Config " + key + " on line 2 is outside the uint64 range");
                }
                String[] invalidBounds = key.equals("x")
                        ? new String[] {"0", "-1", "-2147483648"}
                        : new String[] {"-1", "-2147483648"};
                String minimum = key.equals("x") ? "1" : "0";
                for (String value : invalidBounds) {
                    reject(config, other + key + " " + value + "\n", "must be at least " + minimum);
                }
            }
            reject(config, "x 3\n", "must contain both x and y");
            reject(config, "x 3\nx 4\ny 10\n", "Duplicate config value: x");
            reject(config, "x 3\ny\n", "Config line 2 must be");
            reject(config, "x 3\ny 10 extra\n", "Config line 2 must be");
        } finally {
            Files.deleteIfExists(config);
        }
        System.out.println("PASS: config data types, positive bounds, uint64 bounds, huge values, format errors");
    }
}

