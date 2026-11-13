# Create a collector with an AI agent

Follow this procedure when adapting the starter. Required inputs: customer account URL and target Java package.
Read [AGENTS.md](../AGENTS.md) first. Follow its rules throughout this procedure.

## 1. Analyze sign-in first

- Open the supplied URL. Identify the sign-in page, redirects, form fields, CSRF tokens, and authentication requests.
- Identify credentials, OTP or other two-factor authentication, and OpenID. Identify protections such as DataDome,
  Cloudflare, reCAPTCHA, and other anti-bot challenges. Distinguish observed behavior from assumptions.
- Give the developer a short initial report: sign-in method, detected protections, and unresolved points.
- Check that browser tools allow the developer to sign in and the agent to inspect pages and requests.
  If unavailable, ask the developer to install or configure a suitable browser and tools before continuing browser analysis.
- Explain the next step: open a browser, let the developer sign in, then inspect the customer area to implement the collector
  and adapt `TibuSimulator`. Explain that passwords, OTP codes, and browser session secrets will not be copied into the
  conversation, source code, or saved analysis artifacts.
- Let the developer sign in. Inspect authenticated requests and how to detect a valid session, failed sign-in, and an expired session.
- Analyze the customer area: accounts, transactions, pagination, details, and documents. Identify stable service IDs,
  date and amount formats, currencies, transaction states, and how changed transactions can be found.
  Use these observations for [collector implementation](#2-implement-the-collector).

## 2. Implement the collector

- First rename the Java package in production code and test tools. Update the service registration to match.
- Replace collector metadata, domain permissions, resources, and demo configuration for the target service.
- Implement sign-in, following the implementation rules in `AGENTS.md`
- Use the API version declared in `pom.xml` and its [API and DTO documentation](https://github.com/tibudget/tibudget-api).
  Record unresolved questions instead of inventing behavior.
- Use stable service IDs and preserve existing UUIDs and user choices according to the API.
- A detected anti-bot challenge does not prove sign-in will fail. Leave unsupported challenges unhandled and continue
  the normal sign-in implementation. Report actual failures; do not attempt to bypass protections.
- Generate a first version of the collector, following the implementation rules in `AGENTS.md`.
- Adapt `TibuSimulator` alongside the collector. Instantiate the target collector and replace demo credentials with local runtime input.
- During browser analysis, do not copy passwords, OTP codes, or session secrets into the conversation or saved artifacts.
  Do not enable persistent session storage or network recordings for this analysis.
- Return to the developer with the implemented scope and any points that need their input before the live test.

## 3. Run TibuSimulator

- Use only `TibuSimulator` to validate this first version. Do not create tests or replay scenarios in this workflow.
- Launch the adapted `TibuSimulator`. If tools cannot launch it, explain to the developer how to run its `main()` from the IDE.
- Check sign-in, OTP when applicable, collected data, amount signs, transaction types, and downloaded documents with the developer.
- Inspect progress and resume behavior with the developer, accounting for the simulator limitations documented in `AGENTS.md`.
- Correct discrepancies and run `TibuSimulator` again. Report implemented features, simulator results, and remaining limitations.

## Entry prompt

> Read AGENTS.md and follow docs/create-collector.md to adapt this starter for customer account URL `<URL>` with Java package `<PACKAGE>`.
