# Compile and run

Search for primes using the division and printing modes selected in `src/ps1/variant3/Main.java`.
Requires JDK 21 or newer. No external dependencies.

From this folder in PowerShell:

```powershell
javac -d out (Get-ChildItem ../shared/src,src -Recurse -Filter *.java).FullName
java -cp out ps1.variant3.Main
```

Edit `config.txt` before running. Example:

```text
x 3
y 10
```

This prints 2, 3, 5, and 7. Immediate output includes thread IDs and confirmation timestamps between the
run's start and end timestamps. Elapsed milliseconds includes search and output.
Buffered variants display sorted prime-only records after all workers finish.

Optional config path:

```powershell
java -cp out ps1.variant3.Main "C:/path/to/config.txt"
```

`x` must be an integer from 1 through 9,223,372,036,854,775,807;
`y` may range from 0 through the same signed 64-bit maximum. Values use Java `long`, as permitted with proper input validation. Limits 0 and 1 are valid
with no workers or primes. More workers than candidates or divisors is valid. Large worker
counts can exceed memory or OS thread limits; large searches can be slow.

Missing/unreadable config, missing/duplicate keys, unknown keys, extra tokens,
non-integers, out-of-range integers, and values below their minimum (`x < 1`, `y < 0`) are rejected with an
`Error: ...` message and exit code 1 before searching. Use `x 3`, not `x=3`.
Only one optional command-line argument is accepted.

Immediate output runs on the confirming thread. SearchDivision variants report
worker IDs; DivisorDivision variants report main's ID because main confirms primality.
If main is interrupted, started workers finish before the failure is reported;
buffered records are not displayed on that path. JVM resource errors propagate.

This variant imports common classes from `ps1.shared` in the sibling `shared` folder. The compile command includes shared source and this entry point together. Keep `../shared/src` available when compiling; this folder alone is not sufficient.



