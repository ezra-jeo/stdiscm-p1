# Compile and run

Search for primes using the division and printing modes selected in `src/ps1/variant2/Main.java`.
Requires JDK 21 or newer. No external dependencies.

From this folder in PowerShell:

```powershell
javac -d out (Get-ChildItem src -Recurse -Filter *.java).FullName
java -cp out ps1.variant2.Main
```

Edit `config.txt` before running. Example:

```text
x 3
y 10
```

This prints 2, 3, 5, and 7, with thread IDs and confirmation timestamps between the
run's start and end timestamps. Elapsed milliseconds includes search and output.
Buffered variants display only after all workers finish. Records are not sorted.

Optional config path:

```powershell
java -cp out ps1.variant2.Main "C:/path/to/config.txt"
```

Both `x` and `y` must be integers from 1 through 2,147,483,647. `y = 1` is valid
with no primes. More workers than candidates or divisors is valid. Large worker
counts can exceed memory or OS thread limits; large searches can be slow.

Missing/unreadable config, missing/duplicate keys, unknown keys, extra tokens,
non-integers, out-of-range integers, and values below 1 are rejected with an
`Error: ...` message and exit code 1 before searching. Use `x 3`, not `x=3`.
Only one optional command-line argument is accepted.

Immediate output runs on the confirming thread. Candidate-range variants report
worker IDs; divisor variants report main's ID because main confirms primality.
If main is interrupted, started workers finish before the failure is reported;
buffered records are not displayed on that path. JVM resource errors propagate.

Compile this folder separately from the other variants using its own packaged sources.

