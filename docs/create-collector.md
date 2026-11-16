# Create your first collector

This guide helps you adapt the starter with an AI agent. You need the customer account URL,
the target Java package, Java 21 and Maven. Developers and AI agents must first read
[Collector implementation rules](how-to-implement-collector.md) and [AGENTS.md](../AGENTS.md) for rules specific to this collector.

The developer signs in and checks the meaning of the collected data. The agent inspects the site,
implements the collector and uses the simulator reports to find and fix problems.
A successful HTTP request or a green report alone does not prove that the data is correct.

For an existing collector, follow [Update collector guides](update-collector.md) to update the shared guides
and review the code against changed rules.

## 1. Understand the site

- Open the supplied URL. Identify the sign-in page, redirects, form fields, CSRF tokens and authentication requests.
- Identify credentials, OTP or other two-factor authentication, and OpenID. Identify protections such as DataDome,
  Cloudflare, reCAPTCHA and other anti-bot challenges. Distinguish observed behavior from assumptions.
- Give the developer a short initial report: sign-in method, detected protections and unresolved points.
- Check that browser tools allow the developer to sign in and the agent to inspect pages and requests.
  If unavailable, explain how to install or configure suitable tools before continuing browser analysis.
- Let the developer sign in locally. Do not copy passwords, OTP codes or browser session secrets into the conversation,
  source code or saved analysis artifacts. Do not enable persistent browser session storage or network recordings for this analysis.
- Inspect how to detect a valid session, failed sign-in and an expired session.
- Analyze accounts, transactions, pagination, details and documents. Identify stable service IDs,
  date and amount formats, currencies, transaction states and how changed transactions can be found.

Before implementing, explain what the first version will support and what still needs investigation.
OpenID collectors use the API's OpenID configuration; the current desktop and headless simulators do not support that sign-in flow.
Report this limitation and agree on a suitable host test when it applies.

## 2. Implement a first version

- Give the collector its own project identity. Update the Maven coordinates, name and description in `pom.xml`.
  Replace the starter's SCM links and Git remotes with the collector repository, or remove them if no repository is available yet.
  The adapted project must no longer point to the starter as its own repository. Update the README for the collector and remove the license file.
- Rename the Java package in production code and existing tests. Update `META-INF/services/com.tibudget.api.CollectorPlugin`
  to name the target collector. Replace metadata, domain permissions, resources and demo configuration for the target service.
- Use the API version declared in `pom.xml` and its [API and DTO documentation](https://github.com/tibudget/tibudget-api).
  Follow [Collector implementation rules](how-to-implement-collector.md) for inputs, Internet access, OTP, PDF parsing, state, IDs and UUIDs. Do not invent field rules or website behavior.
- Implement sign-in, accounts, recurring payments when applicable, then transactions and documents.
  Keep the steps easy to follow. Explain any part that is not implemented yet.
- Follow the [mandatory rules for unsupported controls](how-to-implement-collector.md#unsupported-controls-mandatory-behavior).
- Use the generic `com.tibudget.test.simulator.TibuSimulator` supplied by test-utils.
  It discovers the collector through the service registration; do not create a collector-specific launcher or hardcode credentials.
- Replace or remove demo-specific tests as the starter is adapted. Add focused tests for implemented behavior;
  do not keep a passing demo test as evidence that the new collector works.
- Don't keep empty directories

Run `mvn verify`. Fix build and test failures before the first live collect.

## 3. Check the first collect together

From the collector project, launch the simulator with the test classpath:

```sh
mvn test-compile exec:exec@simulate
```

You can also run `com.tibudget.test.simulator.TibuSimulator.main()` from the IDE with the test classpath.
If the agent cannot open the window, give these instructions to the developer.

Open **Settings**, enter credentials locally and use **Run from scratch** for the first check.
The simulator saves validated inputs encrypted under the current OS account. **À propos** explains
local files, recording and simulator limits. **Run** resumes state and cookies; **Stop** requests a graceful stop.

Compare a few collected records with the website, including an older transaction and a recent one:

| Check | What to confirm |
| --- | --- |
| Sign-in | Valid credentials work; invalid credentials and OTP failures produce a clear error. |
| Accounts and recurring payments | IDs identify the right objects; links and user choices are preserved on another run. |
| Transactions | Dates, currency, amount signs, types and states match the website and API rules. |
| History | Pagination reaches older records; transactions are returned oldest to newest. |
| Documents | The right files are linked to the right records and can be opened. |
| Progress and resume | Progress reflects the work; Stop saves progress and another Run resumes without duplicates. |

Fix discrepancies and repeat this check. Keep credentials and documents out of the conversation.
An incomplete first version can be useful, but state its limits before treating its output as a reference.

## 4. Record one run and read its report

Once the first collect makes sense, use the headless tools to make checks repeatable.
They run the same collector without opening a window. Use a test-utils version containing `CollectorCli`.
When working on a local test-utils checkout, run `mvn install` there first and use the matching dependency
in the collector's `pom.xml`, with `test` scope.

In a Bash-compatible terminal, from the collector project:

```sh
mvn test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/agent-classpath.txt

collector_check() {
    java -cp "target/test-classes:target/classes:$(cat target/agent-classpath.txt)" com.tibudget.test.simulator.CollectorCli "$@"
}

collector_check describe
collector_check run --saved-inputs --record --fresh --workspace target/collector-check
```

Use Java 21 for `java` as well as Maven. On Windows, the classpath separator is `;`; the same main class
and arguments can be configured in the IDE. The function above is available in the current terminal session.
`describe` lists the collector inputs. `--saved-inputs` uses the inputs entered in the desktop simulator.
`--fresh` resets only the selected headless workspace, which is separate from the desktop session.

This command contacts the real site and records requests and responses. Raw HAR files, state,
snapshots and downloaded documents can contain credentials and personal data. Keep them local.
For an OTP or counterparty lookup, configure local fixtures as described in `docs/agent-tools.md`
in the test-utils checkout ([headless tools guide](https://github.com/tibudget/tibudget-collector-test-utils/blob/master/docs/agent-tools.md) once published).
The CLI does not prompt interactively for missing values. Use the desktop simulator for interactive checks.

Open `target/collector-check/report.json`:

- `run.complete` says whether the collector finished. It does not say whether its data is valid.
- `run.findings` gives field paths, rule explanations and documentation links. Fix errors and investigate warnings.
- `run.scenario` gives the recorded HAR directory. Copy this path for the next step.
- `result.json` contains results with stable UUIDs for comparisons; `result-raw.json` keeps the original UUIDs.
  These files contain private data. The report does not repeat field values.

Exit code `0` means complete and valid, `2` means paused, `3` means invalid data or a failed comparison,
and `4` means execution failed. A timeout returns `124`; do not treat it as a successful Stop check.
The full guide explains the other codes and options. After changing code, compile again before rerunning the CLI.

## 5. Replay, fix and compare

Once the report has `run.complete: true` and `run.valid: true`, and the developer has checked the data, save a local reference:

```sh
cp target/collector-check/result.json target/collector-reference.json
```

Replace `<recorded-scenario>` below with the `run.scenario` path. Use the same inputs and start from
an empty headless session, as in the recorded run:

```sh
collector_check replay --scenario <recorded-scenario> --saved-inputs --fresh --workspace target/collector-replay --expect target/collector-reference.json
```

Replay uses recorded responses and does not fall back to live HTTP. This lets the agent reproduce a problem
and check a fix without repeatedly contacting the site. The report separates result differences from validation findings.
For each problem, explain the cause, correct the collector, run `mvn verify`, then replay again.

A mismatch in method, URL or POST body identifies a request that differs from the recording.
Inspect that request locally. Changed authentication tokens or a changed request flow may require another scenario;
do not disable checks just to get a passing result. Never replace the reference automatically when a comparison fails.
If a fix intentionally changes the data, review the difference with the developer before updating the reference.

## 6. Check interruptions and changed data

Start with one manual Stop and Run in the desktop simulator. Then, for a suitable complete recording,
use the automated suite with a new workspace:

```sh
collector_check resume-check --scenario <recorded-scenario> --saved-inputs --workspace target/collector-resume-check
```

It requests stop after each recorded response, resumes in new collector instances, checks UUID preservation
and compares the gathered results with an uninterrupted run. It may take longer than a single replay.
If the collector repeats login or navigation on resume, it needs separate continuation recordings;
follow the headless tools guide. A missing continuation does not prove the collector is broken.

Add focused scenarios as the implementation grows: a transaction updated after the last collect, a new record
on an already collected date, a failed download, an expired session or a temporary server error.
The `copy` command can derive a scenario by changing selected responses without editing the original.
Use `--fail-at` for an offline I/O failure. Test changed data in successive runs in the same workspace without `--fresh`.
Compare original UUIDs using raw snapshots; use stable snapshots for independent fresh sessions.

Keep this stage small at first: one complete scenario and one interruption are a useful starting point.
Add a focused regression test for each bug instead of requiring every possible scenario before the first version works.

## 7. Finish with evidence

- Run `mvn verify` and report which live, replay and resume checks passed or could not run.
- State the implemented scope, remaining limitations and any questions about the source data or API.
- Keep private recordings under `target/`. Before adding a scenario to `src/test/resources/scenarios`,
  prepare synthetic or anonymized fixtures, review them manually and verify that replay still works.
  `audit` helps find sensitive content, but a clean audit does not guarantee that a scenario is safe to share.
- Keep only reviewed expected results in regression tests. Files under `target/` are temporary and disappear on `mvn clean`.

## Entry prompt

> Read docs/how-to-implement-collector.md and AGENTS.md, then follow docs/create-collector.md to adapt this starter for customer account URL `<URL>` with Java package `<PACKAGE>`. Guide me through the first live collect, then use the headless tools to validate and replay it. Explain findings and remaining limits in simple terms.
