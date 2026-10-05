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
- Candidate count is `y - 1`: search from 2 through `y`, since 1 is not prime.
- Base range size is `(y - 1) / x`; remainder is `(y - 1) % x`.
- The first remainder-count workers receive one extra candidate each.
- Start at 2; each subsequent range starts at the previous end plus 1.
- A zero-size range has end equal to start minus 1 and performs no search.
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
- Worker reuse versus creation per candidate remains undecided.

### Printing class

Use one class with a mode fixed at instantiation, rather than a printing strategy
hierarchy. Share one printing object across workers.

- Receive complete confirmed-prime records directly; no collection listener.
- Immediate mode prints each record when reported.
- Buffered mode stores records in the printing object's collection and prints
  them after the entire search finishes.
- Protect the reporting operation with the same lock for every caller. A
  `synchronized` instance method can use the shared object's lock.
- Protect complete output records and concurrent buffer additions.
- Keep prime calculations outside the lock.
- Workers do not maintain duplicate prime collections.
- Output order may vary. Sorting buffered output remains undecided.

Each record contains the prime, reporting thread ID, and timestamp captured when
the prime is confirmed, not when buffered output is eventually displayed.

Range division records the confirming worker's ID. Divisor division records
main's ID because main combines findings and confirms the prime. The assignment
explicitly requires IDs and timestamps for immediate output; the agreed design
uses the same record format for both modes. Clarify attribution with the
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

Validate both when loading configuration and at the division method boundary.
Keep the rules consistent and reject invalid inputs before starting workers.

- `x >= 1`.
- `y >= 1`.
- `y = 1` is valid: zero candidates and empty worker ranges, but start and end
  timestamps still appear.
- `x > y` is valid: some workers receive empty ranges and do nothing.
- Detect missing or unreadable configuration and missing or malformed values.
- Do not silently correct invalid values.
- Numeric types, supported upper bounds, null handling, and failure messages
  remain to be specified. Consider overflow and resource limits explicitly.

## Correctness and Performance Verification

Develop verification together as part of learning; no test implementation is
delegated yet.

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

The shared-source layout and packaging/build approach remain undecided: preserve
the modular design while making each variant straightforward to build and run.

## Next Decisions

1. Division interface selected: constructor configuration and parameterless
   `search()`, with interruption reported after worker cleanup.
2. Define the result-record representation and printing-class interface.
3. Choose divisor-worker lifetime and result coordination.
4. Define config format, numeric limits, and validation messages.
5. Decide buffered ordering and four-folder packaging.
6. Implement and review incrementally, then verify and collect performance data.
