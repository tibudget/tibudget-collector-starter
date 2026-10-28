# Implementing a Tibu collector

A collector connects a service to Tibu and returns accounts, transactions, and related data.
Use [StarterCollector](../src/main/java/com/tibudget/plugins/starter/StarterCollector.java)
as an example, then adapt its assumptions to your service.

## 1. Follow the API contract

Read the [Collector API and DTO documentation](https://github.com/tibudget/tibudget-api)
for the API version declared in your POM. Use its Javadoc for required fields,
allowed values, identifiers, and relationships. Do not guess field meanings from their names.

The starter demonstrates one shopping account, one loyalty card, and one subscription.
These are demo choices, not requirements for every collector. In particular, adapt
the fixed time zone, pagination, account lookup, and subscription lookup when your
service has different rules or multiple accounts.

## 2. Keep a clear collect flow

Keep the main steps easy to follow:

1. Reuse a valid session or sign in.
2. Find and update accounts.
3. Find and update recurring payments, if the service has any.
4. Read transaction history and complete each transaction.

Keep website parsing separate from progress management. In the starter,
`readTransactionPage()` returns dated references, `collectTransaction()` builds
one complete transaction, and the caller adds it to the result and saves progress.

Keep the implementation linear and explicit. Add dependencies or abstractions only
when they solve a concrete problem.

## 3. Use the providers supplied by Tibu

Send requests and download files through `InternetProvider`, directly or through
the collector utility methods. Tibu uses this bridge to control network access.
Set `getDomain()` to the target service's root URL and declare the domains needed
by the collector when registering it.

Use the supplied OTP, counterparty, and PDF providers when needed. Do not create
a separate HTTP client in production code. Keep desktop test providers in test scope.

Space requests to respect the service's limits. The demo's request interval is an
example; choose a suitable value for your service.

## 4. Make authentication explicit

Declare configuration fields with `@Input`, mark password fields accordingly,
and implement `validate()` with useful field messages. Put user-facing message
keys and their translations in the collector resources.

Reuse restored cookies when the session is valid. Follow the actual login flow,
including CSRF tokens and hidden form fields. Confirm that authentication succeeded
before parsing account data.

For OTP, use the correct channel and code format. Handle a missing provider,
a missing code, and a refused code. Use the API's connection methods when the
service requires an external authentication flow.

Set a useful configuration name and a stable configuration ID hash. Avoid exposing
a full email address or other personal details in the displayed name.

## 5. Preserve identities and user choices

Use stable service identifiers for accounts, transactions, and recurring payments.
An account ID must identify the same account across runs and collectors for that
service. Do not use a page position or a new random value as its service ID.

Reuse accounts and recurring payments passed to `init()`. Preserve existing UUIDs,
synchronization choices, and user-edited labels as required by the API. Generate a
UUID only for a new entity. The demo refreshes labels directly; adapt that behavior
before using it with user-edited labels.

Do not read transaction history for an account with synchronization disabled.
Return synchronized accounts even when there are no new transactions.

Use the actual counterparty UUID for the service. The demo's generated counterparty
UUID is only an example; look up the appropriate counterparty in Tibu's developer portal.

## 6. Return consistent financial data

Link transactions and recurring payments to their account UUIDs. Link subscription
invoices to the corresponding recurring payment.

Parse amounts using the service's number format and currency. Use the user's point
of view for transaction amounts: a purchase is an expense. Interpret dates in the
account's time zone rather than the machine's default zone.

Map transaction states explicitly. Collect available items, quantities, payments,
photos, and invoices according to the DTO rules. Distinguish an optional document
that has no link from a document whose download failed.

## 7. Process history from oldest to newest

Read history one page at a time and complete its transactions before requesting
the next page. Avoid loading every transaction detail before returning useful work.

Dates must remain in ascending order across page boundaries. Sorting each page
alone is not enough. If orders and subscription invoices come from separate lists,
merge them into one chronological history before advancing progress.

Keep the search range consistent while following page tokens. Adapt overlapping
date ranges to the service so page boundaries do not hide transactions.

## 8. Save progress only after complete work

A collect may span several runs. Restore progress from the settings passed to
`init()`; do not rely on fields from a previous collector instance.

The starter saves the last completed date and the references completed on that date.
On resume, skip older dates and saved references, but still accept new references
on the same date. A date alone is not enough to prevent both duplicates and missing data.

Add a transaction and save its progress only after its required details and documents
are complete. If that work fails or stops, leave its reference available for retry.
When adapting the starter for multiple accounts, track progress separately for each
account so one account cannot cause another account's history to be skipped.

## 9. Stop gracefully and report failures clearly

Return `true` only when the collect is finished, and `false` when more work remains.
Honor stop requests through the collector utilities and preserve completed work.
In the current base class, a stop requested before the collect still allows one
transaction before it takes effect.

Do not save a partially built transaction as complete. The starter treats a null
result from `collectTransaction()` as unfinished work and catches `MustStopException`
at the collect boundary.

Use the API exceptions for their documented purpose: invalid parameters, refused
access, connection failures, temporary unavailability, or parsing and collect errors.
Do not silently treat a changed page structure as an empty history.

## 10. Test both the flow and the website rules

Use saved responses for routine tests. Each test should explain the behavior it
protects and use only the data needed for that scenario.

| Tool or test | Purpose |
| --- | --- |
| `StarterCollectorTest` | Check pagination, progress, same-date references, stop requests, and disabled synchronization with in-memory data. |
| `StarterWebsiteTest` | Exercise real HTML parsing with simulated responses, including authentication and download failures. |
| `TibuSimulator` | Inspect a live collect and record responses and expected results during development. |
| `ReplayScenariosTest` | Replay recorded responses and compare the result with `collect.json`. |

For replay, reproduce the original configuration and initial settings, accounts,
and recurring payments. Review result differences before changing expectations.
A successful full-history replay does not cover interruption, invalid credentials,
or resume behavior by itself.

The simulator currently restores settings, but not previous accounts or recurring
payments. Use explicit test setup when checking identity preservation across runs.

## 11. Protect credentials and personal data

Keep real credentials, session cookies, OTP codes, and personal data out of source
code, logs, and committed recordings. Prefer demo accounts. Review both recorded
requests and responses before sharing a scenario.

## 12. Verify the build and publish the right archive

Use Java 21 and Maven, keeping production code and test tools in their standard
Maven directories. Declare the host API with `provided` scope and test tools with
`test` scope. Use one consistent set of API coordinates and compatible library versions.

Replace the demo project name, package, service registration, messages, logo, domain,
and collector metadata. Update the service registration if you rename the collector class.

Run `mvn verify` with the Android SDK configured. Check the tests and DEX generation;
a successful Java compile alone does not validate the Android build.

Before publishing, increase `collector.version.code` and review the vendor and default
locale. Upload `target/*-jar-for-tibu.jar`, which includes runtime dependencies and the
Tibu manifest. The separate `*-dex.jar` validates Android compilation; it is not the
archive to upload. See the [README](../README.md) for build requirements and outputs.
