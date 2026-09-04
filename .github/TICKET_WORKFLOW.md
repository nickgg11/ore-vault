# Ticket workflow

How issues and PRs get written, updated and closed in this repo.

Written after the 2026-09-03 audit, which found 30 closed issues carrying unticked acceptance
criteria, a ticket closed `NOT_PLANNED` that read as completed, an epic section headed "Open
playtest items" containing nothing but closed items, and a delivered ticket left open because
the PR that shipped it never said `Closes`. Every rule below exists because one of those
happened. None of it is generic advice.

## Writing a ticket

- **Title carries the work-item number and the thing**: `[76] DropPipeline — ordered stages for
  every node that modifies ore drops`. The bracketed number is the plan's task id; the issue
  number is GitHub's. They are not the same and both get used, so never write one meaning the
  other.
- **Open with the spec reference.** `Spec: §11, §4.2`. A ticket whose behaviour is not in the
  spec is a ticket that will be implemented twice differently.
- **Say why before what.** The reader deciding whether to pick this up needs the problem. A
  paragraph of motivation beats a longer task list.
- **Acceptance criteria are checkboxes, and each one must be checkable by a named means.** "Works
  correctly" is not a criterion. "No orb spawns for a machine-broken block, covered by a test" is
  — and if a test cannot exist, say what does verify it instead. Half the audit's exceptions were
  criteria demanding unit tests for Minecraft-side code that this source set cannot load.
- **Mark what is blocked, with the blocking issue number.** A criterion that cannot be met until
  another ticket lands should say so when it is written, not be discovered at close time.
- **Record rejected alternatives.** #100 records why gating anchor *placement* was rejected in
  favour of gating the recipe. That paragraph stops the question being reopened every time
  someone reads the ticket fresh.

## Updating a ticket

- **Scope changes get an amendment block, dated, at the top of the body** — not a silent edit.
  `> **Amended 2026-08-29 — scope split.** …` Silent edits destroy the record of why the plan
  moved.
- **When a capability moves between tiers, phases or tickets, grep for the old number.** #100
  moved instant travel from tier 4 to tier 3; the tier-4 wording survived in two javadoc blocks
  and in this epic's checklist. A scope change is not applied until the references agree.
- **Keep the epic body short and current.** History belongs in comments. If a section header
  says "open" it must contain only open things; the roadmap is what people come to the epic for
  and anything above it is in the way.

## Closing a ticket

Work through this before closing. It is the part that was skipped.

1. **Tick every criterion that is met.** Do this from evidence, not memory — the build output,
   the test count, the file you just read.
2. **Leave unmet criteria unticked and comment why**, naming the blocking issue. An unticked box
   with an explanation is useful; an unticked box with no comment is indistinguishable from
   neglect, which is what 30 issues looked like.
3. **State the evidence in a close-out comment.** Which PR delivered it, whether the build and
   tests were green, whether it was seen in a client. "Merged" is not evidence that it works.
4. **Use the right close reason.** `COMPLETED` for done. `NOT_PLANNED` for superseded, split or
   abandoned — and when a ticket is split, name the tickets it split into. #56 and #32 were both
   `NOT_PLANNED` and both read as finished until someone checked the close reason.
5. **A ticket delivered but not closed is a bug in the process.** Put `Closes #N` in the PR body
   for every ticket the PR finishes. #23 shipped in #116 and stayed open for five days because
   the body mentioned it without the keyword.

## Pull requests

- **Body says what changed and why, then how it was verified.** The verification section is not
  optional and not decorative: name the commands and the actual numbers. `BUILD SUCCESSFUL in 2s`
  from Gradle's configuration cache is indistinguishable from a no-op — read
  `build/test-results/test/*.xml` and quote the test count.
- **Say what is *not* verified.** Renderers, screens and command handlers cannot be unit tested
  here. A PR touching them says so and says what would catch a regression instead
  (`runGameTestServer` for dedicated-server class-load safety; a client playtest for anything
  visual).
- **Disclose process deviations rather than implying compliance.** If the code was written before
  its tests, the PR says so. Tests written after characterize behaviour; they do not prove it.
- **End with "ready to merge", in those words.** Opening a PR is not the same as being finished
  with it, and follow-up commits land minutes later. Do not leave the state to be inferred.
- **Stacked PRs name their base and the merge order.** GitHub retargets a stacked PR to `main`
  automatically when its base merges, but only the PR body tells a human which goes first.

## After a merge

- `delete_branch_on_merge` is on, so the branch is gone. Never push follow-ups to it — branch
  again from `main`.
- Update the spec's §13 checklist in the same PR as the behaviour. The audit found ten §13 boxes
  unticked for work merged weeks earlier, and one of them instructed the reader to delete code
  that was deliberately kept.
- A green check on the review workflow does not prove a review ran. See CLAUDE.md.
