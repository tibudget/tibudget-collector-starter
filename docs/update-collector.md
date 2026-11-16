# Update collector guides

This workflow applies to developers and AI agents maintaining an existing collector.
Update the shared guides from the official starter, then review the collector against the changed rules.
Updating the guides does not authorize changes to the collector code.

## 1. Get the current guides

Use the official source: <https://github.com/tibudget/tibudget-collector-starter>.
Fetch its default branch into a separate temporary checkout. Do not change the collector's Git remotes
or merge the starter into the collector repository.
Record the source commit so the update can be traced and all three files come from the same revision.
If the source cannot be read, report the problem and do not replace the local guides with incomplete content.

Read the source version of this update guide before proceeding. Keep the update limited to these files:

- `docs/update-collector.md`
- `docs/create-collector.md`
- `docs/how-to-implement-collector.md`

## 2. Update the shared guides

Compare the local and source versions before editing. Keep a copy of the previous versions for the review.
Identify changes to rules, including additions, removals and changes to the validation workflow.

Update the three files to the source revision. Preserve unrelated local changes and any existing work in progress.
If a guide contains collector-specific instructions, preserve them in `AGENTS.md` before replacing the shared text.
Do not overwrite or replace `AGENTS.md`; keep its existing rules and add only the instructions being preserved.
If local changes cannot be reconciled safely, explain the conflict and ask the developer how to resolve it.

Check the final diff and relative documentation links. Report the source commit and summarize the changed rules.
For documentation-only updates, Maven and the simulator do not need to run.

## 3. Review the collector without changing code

After updating the guides, review the current collector against the changed rules.
Read `AGENTS.md` for collector-specific context. Inspect the relevant code, tests and configuration;
use the documented API contracts when a rule depends on API behavior.

Keep this review focused on the updated rules. Do not change code, tests, dependencies or configuration,
and do not automatically fix findings. Do not start a live collect or create network recordings for this review.

Give a short report:

- Summarize which changed rules the current implementation follows, with supporting file references.
- List each gap with the affected file and line, the updated rule and the practical consequence.
- Distinguish confirmed gaps from points that cannot be checked from the available code or documentation.
- State what was inspected, what checks ran and what could not be verified. Do not claim runtime compliance from source inspection alone.

If code changes are needed, ask: "Would you like me to update the collector code to follow the new rules?"
Wait for the developer's answer before changing code. If no gaps were found, say so and report any remaining uncertainty.

## Entry prompt

> Follow docs/update-collector.md to update the three shared guides from the official starter. Then review the current collector against the changed rules without modifying code. Report findings and ask before implementing any fixes.
