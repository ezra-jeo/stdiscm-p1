# Basic threading: prime search

Four Java variants search for primes from 2 through `y`, using `x` workers.
Both values come from a separate config file.

| Folder | Division | Printing |
| --- | --- | --- |
| `variant1-range-immediate` | Candidate ranges | Immediate |
| `variant2-range-buffered` | Candidate ranges | After all workers finish |
| `variant3-divisor-immediate` | Divisor ranges per candidate | Immediate |
| `variant4-divisor-buffered` | Divisor ranges per candidate | After the entire search |

## Requirements

Install JDK 21 or newer and put `java` and `javac` on PATH. No external dependencies.
Check with `java -version` and `javac -version`.

## Config

Each folder includes `config.txt`:

```text
x 2
y 100
```

Use one key and integer per line, separated by whitespace. Blank lines are allowed.
Both keys are required, once each. Unknown keys and extra tokens are rejected.
Values must be integers from 1 through 2,147,483,647. `y = 1` is valid and prints
no primes. `x > y` is valid; some jobs have empty ranges.

The numeric limit is a representation limit. Available memory and OS thread
limits can prevent large worker counts from running. Large search limits can
take a long time, especially with new workers created for every candidate.

## Compile and run

From the project root, using PowerShell:

```powershell
cd variant1-range-immediate
javac -d out (Get-ChildItem ../shared/src,src -Recurse -Filter *.java).FullName
java -cp out ps1.variant1.Main
```

For variants 2, 3, and 4, use `ps1.variant2.Main`, `ps1.variant3.Main`, and
`ps1.variant4.Main` respectively, from the corresponding variant folder.
Each variant imports common classes from `ps1.shared`, outside its own package.
The compile command includes both `../shared/src` and the variant's `src` and
places their compiled classes together in `out`. Keep the shared folder beside
the four variants; compiling a variant's entry point alone is insufficient.

To use another config file:

```powershell
java -cp out ps1.variant1.Main "C:/path/to/config.txt"
```

Without an argument, `config.txt` is resolved relative to the current directory.
For `x 3` and `y 10`, the prime records contain 2, 3, 5, and 7 exactly once.
Each run prints a start timestamp, prime records, an end timestamp, and elapsed
milliseconds. The elapsed duration includes searching and buffered display.

Each prime record includes its confirming thread ID and confirmation timestamp.
Candidate-range division reports worker IDs. Divisor division reports main's ID
because main combines the results. Range output order depends on scheduling;
buffered records retain reporting order and are not sorted.

## Errors

Input errors print `Error: ...` to stderr and return exit code 1. Invalid config
is rejected before the search starts, so it has no run timestamps.

| Input or situation | Error |
| --- | --- |
| Missing or unreadable config; directory used as config | `Cannot read config file: ...` |
| Missing `x` or `y`; empty file | `Config must contain both x and y.` |
| Repeated key | `Duplicate config value: x.` or `y.` |
| Unknown key, missing value, extra tokens, or `x=2` format | `Config line N must be x <integer> or y <integer>.` |
| Decimal, text, or integer outside the 32-bit range | `Config x/y must be a 32-bit integer.` |
| `x < 1` | `Worker count must be at least 1.` |
| `y < 1` | `Search limit must be at least 1.` |
| More than one command-line argument | `Usage: java -cp out <variant-main> [config-file]` |
| Interrupted coordinator | `Search interrupted. All started workers have finished.` |

Interrupted searches wait for started workers before reporting the interruption.
Buffered results are not displayed on that failure path; an end timestamp still
appears. JVM resource failures, such as failure to start a native thread, propagate
as Java errors. Wrong Java versions or classpaths can cause compilation or class
loading errors; those are build errors rather than config errors.

## Edit and verify

VS Code source roots are configured in `.vscode/settings.json`. After changing
the layout, reload the editor window if old diagnostics remain.

Common classes exist once in `shared/src/ps1/shared`. Each variant has a small
`src/ps1/variantN/Main.java` importing the shared runner. Tests use `ps1.shared`
so they can access package-private jobs. Edit common classes directly; no copies
or synchronization step are needed.

Run all checks from the project root:

```powershell
./scripts/verify.ps1
```

Verification compiles each variant with shared source, runs the two
scheme test sets, and checks the entry points with valid and invalid config files.
Build output goes into a unique temporary directory. Assertion failures or
nonzero exit codes fail the verification script.

## Submission status

Source, four entry points, separate configs, compile instructions, and automated
checks are present. See `plan.md` for the completeness audit.

The video, presentation slides, performance measurements, and submission ZIP
remain. Include `shared` alongside the four variant folders in the ZIP, since
all four depend on it. Include their source, config, and README. Exclude compiled `.class`
files and `out` folders. Old root `.class` files are not used by these builds.

For performance analysis, keep `y` and timing boundaries fixed, vary `x`, repeat
measurements, and account for JVM warmup. Discuss console output, the reporting
lock, uneven prime-check costs, and per-candidate thread creation and joining.

