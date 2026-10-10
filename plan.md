# Basic Threading Assignment Plan

## Workflow

Stay in hand mode: Ezra writes the implementation; Codex guides design,
explains concepts, and reviews attempts. No implementation is delegated unless
Ezra explicitly changes modes. Use Java; Node.js and Python are prohibited.

## Objective

Search for primes in the inclusive range 1 through `y` using `x` worker threads.
Read `x` and `y` from a separate configuration file.

Produce four variants:

| Variant | Division | Printing |
| --- | --- | --- |
| 1 | Candidate ranges | Immediate |
| 2 | Candidate ranges | Buffered |
| 3 | Divisor checks per candidate | Immediate |
| 4 | Divisor checks per candidate | Buffered |

## Agreed Architecture

### Division strategies

One `DivisionScheme` interface unifies both implementations. Each implementation
receives the worker count, inclusive search limit, and shared printing object
through its constructor. The interface exposes `void search() throws
InterruptedException`: results are reported through the printing object.
Successful return means all search workers have finished. An interruption is
reported only after all started workers have finished.

Each strategy creates, starts, coordinates, and joins its workers. Coordination
executes on the application main thread; creating a strategy object does not
create another thread. No separate coordinator thread is needed.

#### Candidate-range division

- Each worker independently checks the candidates in its assigned range.
- Return before allocating workers when `y < 2`. Otherwise candidate count is
  `y - 1`: search from 2 through `y`.
- Base range size is `candidateCount / x`; remainder is `candidateCount % x`.
- The first remainder-count workers receive one extra candidate each.
- Start at 2; each subsequent range starts at the previous end plus 1.
- A zero-size job uses the canonical empty range 2–1 and performs no search.
- Every candidate must be assigned exactly once, with no gaps or overlaps.
- Workers report confirmed primes directly to the shared printing object.

Example: `y = 10`, `x = 3` produces 2–4, 5–7, and 8–10.

#### Divisor division

- Process candidate numbers sequentially.
- For each candidate, divide its necessary divisor checks among workers.
- Workers report whether they found a divisor, not whether the candidate is prime.
- One discovered divisor proves compositeness; primality requires all necessary
  checks to finish without finding a divisor.
- Main combines findings and reports confirmed primes.
- Workers do not own prime collections.
- Create fresh workers and job objects for each candidate. Main retains a
  thread-to-job map, joins all threads, then reads each job's boolean finding.

### Printing class

Use one class with a mode fixed at instantiation, rather than a printing strategy
hierarchy. Share one printing object across workers.

- Reporting receives a `PrintingSchemeObject` record with thread ID,
  confirmation timestamp, and prime number instead of a preformatted string.
  The typed record replaces the originally proposed map. One thread can report
  multiple primes without overwriting earlier reports.
- Immediate mode prints each prime with its thread ID and timestamp when reported.
- Buffered mode stores records in the printing object's collection and prints
  them after the entire search finishes.
- Protect the reporting operation with the same lock for every caller. A
  `synchronized` instance method can use the shared object's lock.
- Protect complete output records and concurrent buffer additions.
- Keep prime calculations outside the lock.
- Workers do not maintain duplicate prime collections.
- Immediate output order may vary. Buffered output sorts records by
  numeric prime value in ascending order, then displays only the prime numbers.

Each record contains the prime, reporting thread ID, and timestamp captured when
the prime is confirmed, not when buffered output is eventually displayed.

Range division records the confirming worker's ID. Divisor division records
main's ID because main combines findings and confirms the prime. The assignment
explicitly requires IDs and timestamps for immediate output. Buffered
output omits these fields, while retaining them in the structured reports.
Clarify attribution with the
instructor if worker IDs specifically are required for divisor division.

### Main entry points

Keep each variant's entry point thin:

1. Read and validate configuration.
2. Select the division strategy and printing mode.
3. Print the run's start timestamp.
4. Invoke the search and wait for completion.
5. Print buffered records when applicable.
6. Print the run's end timestamp.

## Validation Contract

Validate when loading configuration and in each configured strategy's constructor.
Configuration fields are final; `search()` uses those already-validated values.
Keep the rules consistent and reject invalid inputs before starting workers.

- `x >= 1`.
- `y >= 0`; negative search limits remain invalid.
- `y = 0` and `y = 1` are valid empty searches; start and end timestamps
  still appear. Range division returns early; divisor division's loop starts at
  `FIRST_PRIME_CANDIDATE = 2` and never executes for these limits. Neither
  creates workers for an empty search.
  Keep `x >= 1` validation even for an empty search.
- `x > y` is valid: some workers receive empty ranges and do nothing.
- Detect missing or unreadable configuration and missing or malformed values.
- Do not silently correct invalid values.
- Config uses one `x <integer>` and one `y <integer>` line; blank lines are allowed.
  Missing keys, duplicate keys, unknown keys, extra tokens, and malformed numbers
  are rejected. Errors are documented in `README.md`.
- Config validation distinguishes invalid integer syntax from values above uint64
  max, identifying the key and line. Negative values are rejected against each
  field's minimum: 1 for x and 0 for y. Decimals are never rounded or coerced.
- Both x and y are BigInteger values bounded by uint64 max:
  18,446,744,073,709,551,615. Candidate counters, range endpoints, and prime
  records also use BigInteger, including increments past the final endpoint.
- Divisor bounds use the exact BigInteger.sqrt() result, converted with
  longValueExact(). The largest checked divisor is 4,294,967,295, so divisor
  loops fit in signed long. No floating-point roots or int narrowing are used.
- This numeric limit does not guarantee feasible thread counts or runtime.
  Do not attempt full maximum-input searches in boundary tests.
- Null printing dependencies are rejected in strategy constructors. OS thread
  and memory limits can still cause startup failures.

## Correctness and Performance Verification

Test implementation and packaging were explicitly delegated. Run
`scripts/verify.ps1` for scheme tests and all four entry points with shared source.

- Compare prime results with known expected results, regardless of output order.
- Check small limits, perfect squares, uneven divisions, and empty worker ranges.
- Check missing, malformed, and invalid configuration values.
- Check that concurrent reporting preserves every record exactly once.
- Repeat runs to expose concurrency issues.
- Ensure all workers finish before buffered output and the end timestamp.
- Use wall-clock timestamps for human-readable logs and `System.nanoTime()` for
  elapsed durations.
- Compare thread counts with the same search limit and measurement boundaries.
- Use repeated measurements and account for JVM warmup.
- Analyze console-output cost, lock contention, workload imbalance, thread
  creation, and per-candidate coordination/join overhead.

## Deliverables

- Source for all four variants.
- Video demonstration of all four variants.
- One ZIP organized into four separate variant folders.
- Build and compilation instructions.
- Presentation slides analyzing implementation and performance characteristics.

Common source lives in `shared/src/ps1/shared` under package `ps1.shared`. Four variant folders each
contain `src/ps1/variantN/Main.java` in package `ps1.variantN`, importing common
classes from `ps1.shared`, plus `config.txt` and a README:
`variant1-range-immediate`, `variant2-range-buffered`,
`variant3-divisor-immediate`, and `variant4-divisor-buffered`.
Compile each variant with `shared/src`; the README documents the command.
There are no source copies or synchronization script. Include `shared` alongside
the four variant folders when packaging, since it is their common dependency.

## Completed Implementation To-Do

- [x] Allow both x and y through uint64 max using bounded BigInteger; update
  allocation, candidate counters, reporting, validation matrices, and boundary tests.
- [x] Accept search limits 0 and 1 without creating workers; preserve run
  timestamps. Config/strategy validation, tests, and README errors are updated.
- [x] Buffered output (variants 2 and 4) displays primes without thread IDs or
  confirmation timestamps. Immediate output keeps both fields.
- [x] Replace string reports with typed `PrintingSchemeObject` records instead
  of the originally proposed maps. Sort buffered primes numerically in ascending
  order. Tests cover deliberately shuffled reports, exact buffered formatting,
  repeated display, immediate record fields, and all four entry points.

Verified with `scripts/verify.ps1`: all config, scheme, uint64 boundary, and entry point checks pass; the updated validation audit passed 605 scenarios.

## Pending Implementation To-Do

- [ ] Change B2 (divisor division) to reuse worker threads across candidates
  instead of creating a fresh batch for each candidate. Apply to both printing
  modes. Reset per-candidate findings, wait for all divisor checks before
  confirming each prime, and shut down/join all workers before search returns.
  Update tests and performance notes for the new worker lifecycle.

## Current Deliverable Status

- Complete: four variant source folders and thin main entry points.
- Complete: separate config files, validation, and documented input errors.
- Complete: synchronized shared reporting; immediate or post-search display.
- Complete: confirmation IDs/timestamps, run start/end timestamps, and elapsed
  durations using `System.nanoTime()`.
- Complete: compile/run instructions, variant builds including shared source, scheme tests,
  and entry point checks for valid/invalid inputs.
- Verification limitation: candidate-range tests check primes and worker counts,
  not exact composite-candidate coverage. Divisor tests check exact allocation.
  Startup resource failures and worker exception propagation are not covered.
- Remaining: performance data and analysis, video demonstration, presentation
  slides, and final submission ZIP. ZIP the four variant folders and their
  required `shared` folder; exclude compiled output.
- Instructor clarification remains: divisor output attributes confirmation to
  main. Verify whether the instructor expects divisor-worker IDs instead.

