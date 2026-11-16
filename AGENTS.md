# Development rules

These rules apply to humans and coding agents working in this repository.
For the developer onboarding steps, follow [Create a collector](docs/create-collector.md).

## Scope and structure

- Use Java 21 and Maven. Do not add Gradle support.
- Keep the standard Maven layout: production code in `src/main/java`, tests in
  `src/test/java`, and resources in the matching `resources` directories.
- Keep changes scoped to the request and keep the project simple. Preserve unrelated local changes and existing behavior unless changing them is explicitly required.
  Add dependencies, frameworks, abstractions or broad refactors only when a concrete requirement of the requested change needs them.
- When implementation begins, use the Tibu Collector API documentation and
  verified API contracts. Do not guess API behavior.
- Preserve existing work and the existing license file.

## Collector runtime rules

- Request user data, such as a username or password, through member variables annotated with `@Input`.
  For OpenID sign-in, implement `getOpenIdJSONConfiguration()` instead of requesting credentials through `@Input`.
- All Internet access in collector code must go through the supplied `InternetProvider` or methods that use it.
  Any other Internet access will be rejected. This restriction protects users.
  Do not use separate HTTP clients, direct network connections, or library methods that bypass `InternetProvider`.
- Request every authentication code through the supplied `OTPProvider`. It will try to retrieve the code automatically
  or ask the user to enter it manually.
  Always specify the channel used to send the code, such as email, SMS, or another channel.
  Include the site's help message when one is available to guide the user.
  Set the expected code pattern whenever it is known.
- Collect transactions from oldest to newest. Save the collector state regularly so it can resume where it stopped
  when asked to stop.
  Use the collector settings to store its state, progress, and current stage for this purpose.
  If transactions have changed since the last collect, retrieve them again so they are updated in Tibu.
- Write collected account, transaction, item and recurring payment labels in the provider website's language.
  Preserve the site's original wording when available; generated labels must use the same language (for example, French for a French website).
  Preserve existing labels changed by the user. This rule does not change the English requirement for code names and comments.
- Identify recurring payments and installment schedules across invoices, documents and the customer area. Use schedules in invoices
  or other contractual documents first to define each plan and its linked transactions, using the stated dates and amounts.
  Then reconcile past and future debits found elsewhere: update matching installments with confirmed payment details and add only
  debits not already represented. Prefer shared service references; otherwise cross-check the account, plan, dates and amounts.
  Do not merge ambiguous matches or count the same expense both as installments and as a full invoice transaction.
  Associate supporting invoices with the plan where the API allows it, and follow the API rules for attachments and transaction states.
- Return precise and correct data. Consult the Tibu Collector API and DTO documentation to determine which information
  to provide and how to fill each field, including the amount sign and transaction type. Do not guess field meanings or rules.
- When an invoice contains payment information, extract it and add it to the associated transactions whenever the Tibu Collector API supports it.
  This includes the card number or visible card digits, and the SEPA mandate reference used for payment.
  Preserve the information as shown in the invoice; do not guess missing or masked details. Follow the API and DTO documentation to choose the correct fields.
- On error, throw the appropriate exception and describe the problem precisely to make debugging easier.
  Log the information needed for a developer to find the cause of the problem.
  Do not include sensitive information in error messages, logs, or other diagnostic data.
- Use the supplied `PDFToolsProvider` to parse PDF files; don't use an other way to parse PDF.

### Attached documents: mobile-readable formats

- Attached documents MUST be readable in the mobile application. Use PDF or a supported image format; plain text is an acceptable fallback when it preserves the complete document content in a readable form.
- HTML and other formats that the mobile application cannot display MUST NOT be attached as-is. Convert them to a supported format before creating the `FileDto`. Changing the file extension or MIME type is not conversion.
- Preserve all invoice information during conversion, including seller details, invoice reference, dates, items, amounts, taxes and payment information. Verify the actual output format and readable content before attaching it.
- Prefer a conversion tool supplied by the host API when available. Conversion must work in the collector's mobile runtime; a desktop-only browser, shell command or simulator feature is not a valid runtime solution. If conversion is unavailable, report the document limitation explicitly instead of attaching an unreadable file.

### Unsupported controls: mandatory behavior

- **MUST continue past unsupported controls.** When a page or action declares a CAPTCHA, reCAPTCHA, anti-bot check or other control that the collector does not implement, ignore that unsupported step and execute the remaining normal workflow, including submitting the normal request. This is mandatory even when the control appears in the selected action's sequence.
- **MUST NOT block preemptively.** The presence of an unsupported control must never, by itself, cause a local exception, an early return, a skipped submission, a failed collect or a request for user intervention. Do not infer server rejection from page markup or action metadata. Report a rejection only when the actual server response establishes it.
- An optional informational log may state: `Unsupported reCAPTCHA step detected; ignoring this step and continuing the normal request.` Do not generate fake challenge results, claim that a challenge succeeded or implement a protection bypass. Continue handling supported authentication steps, including email or SMS codes through `OTPProvider`.

## Code and documentation

- Write code for human maintainers first. The code must be easy to understand for developers who may not be fluent in
  English and may not know the whole project.
- Preserve existing comments and Javadoc unless they are outdated, inaccurate, or no longer reflect the actual behavior of the code.
- Use term 'collect', not 'collection'
- Use simple English in names, comments, Javadoc, validation messages and documentation. Avoid uncommon words,
  abbreviations and unnecessarily technical wording when a simpler formulation exists.
- Prefer explicit and straightforward code over clever abstractions, compact tricks or complex constructs.
- Use clear and descriptive names. A developer should understand the purpose of a variable, method or class without
  needing to inspect unrelated parts of the project.
- Keep methods easy to read as a sequence of logical steps. When a method performs several distinct operations, make
  these steps visually clear.
- Use short comments to introduce important steps inside a method when this makes the execution flow easier to
  understand. Comments should explain the purpose of the step, not repeat what the following code already says.
- Keep related statements together and use blank lines to separate logical steps. Do not add blank lines between
  statements that belong to the same operation.
- Avoid unnecessary line breaks. The IDE line width is 200 characters, so short and reasonably sized signatures, method
  calls, conditions and expressions should usually stay on one line.
- Break a long statement when separating it makes its structure or the different steps easier to understand. Do not
  mechanically wrap arguments, chained calls or expressions just because they could be split.
- Follow the formatting style of the surrounding code when it does not conflict with these readability rules.
- Javadoc and documentation for classes and methods must use a multi-line format, even when the documentation is short.
- In Javadoc, use `{@link ...}` when referring to a class, method, field, or constant. Link to public getters when DTO fields are private.
- Keep Javadoc short and use simple English for readers who are not native English speakers. Explain what to implement in this collector.
- Link to the API and DTO documentation for field rules instead of repeating them here. Keep only essential guidance, such as stable IDs and preserving existing UUIDs.
- Comments, Javadoc and validation messages must be written in English.
- Explain non-obvious behavior, business rules, assumptions and important implementation decisions. Do not add comments
  that simply restate the code.
- Do not split code into additional methods or classes only to make methods shorter. Extract code when the resulting
  method has a clear responsibility, is reusable, or makes the main execution flow significantly easier to understand.
- Prefer a slightly longer but linear and explicit implementation over a shorter implementation that requires more
  knowledge of the project to understand.
- Use constructor injection for new dependencies.
- Keep controllers focused on HTTP binding, authorization and responses. Put business logic in services and database
  queries in repositories.
- Keep credentials and personal data out of source code, resources, logs, and
  committed test data.
- Keep the README and `docs/` accurate as the project grows.

## Verification

- Run `mvn verify` after build or code changes unless the requested workflow specifies a different validation process.
- Add focused tests for new behavior unless the requested workflow excludes test creation. Do not add
  placeholder tests just to make the empty starter appear tested.
- Run `com.tibudget.test.simulator.TibuSimulator.main()` with the test classpath. The generic simulator stores local state,
  downloads and optional HAR recordings under `target/com/tibudget/test/<collector-class>/`. Review these files for sensitive data before sharing or committing.
- The simulator restores settings, accounts and recurring payments. Check that the collector preserves existing UUIDs and user choices when resuming.
- Follow `docs/create-collector.md` to add headless validation, offline replay and stop/resume checks after the first live collect.
  Keep raw recordings local; only add reviewed synthetic or anonymized scenarios to `src/test/resources/scenarios`.
- Report what changed, what was verified, and any checks that could not run.
