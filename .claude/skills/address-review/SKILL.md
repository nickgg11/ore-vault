---
name: address-review
description: Pull the open review comments from a GitHub PR and fix them. Use when a PR has received review feedback — from Claude's automated review or from a human — and the comments need triaging, fixing, and replying to. Triggers on "address the review", "fix the PR comments", "what did the review say".
---

# Address PR review comments

Fetch every unresolved review comment on a pull request, fix what should be fixed,
push, and reply on each thread so the reviewer can see what happened.

## 1. Find the PR

If the user gave a number, use it. Otherwise infer it from the current branch:

```bash
gh pr view --json number,title,headRefName,url
```

No PR for the branch → say so and stop. Don't guess at another one.

## 2. Fetch the comments

Resolution state lives only in GraphQL, so use it for inline threads — REST will
happily hand you threads that are already resolved.

```bash
gh api graphql -f query='
{
  repository(owner: "nickgg11", name: "ore-vault") {
    pullRequest(number: PR_NUMBER) {
      reviewThreads(first: 100) {
        nodes {
          id
          isResolved
          isOutdated
          path
          line
          comments(first: 20) {
            nodes { author { login } body }
          }
        }
      }
    }
  }
}'
```

Then the top-level conversation, which inline threads don't cover:

```bash
gh pr view PR_NUMBER --json reviews,comments
```

**Skip** threads where `isResolved` is true. **Report but don't auto-fix** threads
where `isOutdated` is true — the code moved under them, so the comment may no longer
apply; ask before acting.

## 3. Triage before fixing

Read `CLAUDE.md` first — several review findings will be about conventions documented
there, and a few will be findings that *contradict* it (an automated reviewer
suggesting `ResourceLocation`, or unit tests for code that can't have them).

Sort each comment into one of three buckets and say which:

- **Fix** — the finding is correct. Make the change.
- **Push back** — the finding is wrong, or right in general but wrong for this repo.
  Say why, cite the file/line or the `CLAUDE.md` rule. Do not make the change.
- **Ask** — the finding is a real design question the user should settle.

A reviewer being an AI does not make it right, and it does not make it wrong. Verify
each claim against the actual code before acting on it. If a finding asserts a
behaviour, confirm that behaviour exists at a specific `file:line` rather than
trusting the description.

**Never** mark a thread resolved for a finding you didn't actually address.

## 4. Fix

Group related fixes into coherent commits rather than one commit per comment. Follow
the repo's `feat:`/`fix:`/`docs:` convention with the issue number.

Run the tests before pushing:

```bash
./gradlew test
```

If a fix touches something the unit tests can't reach — anything loading Minecraft
classes — say so plainly in the reply instead of implying it was verified.

## 5. Reply and push

Push the branch, then reply on each thread you acted on:

```bash
# Reply to a specific inline thread
gh api graphql -f query='
mutation {
  addPullRequestReviewThreadReply(input: {
    pullRequestReviewThreadId: "THREAD_ID"
    body: "Fixed in <sha> — <one line on what changed>."
  }) { comment { url } }
}'
```

For findings you pushed back on, reply with the reasoning rather than staying silent.
An unanswered review comment reads as ignored.

Resolve a thread only after you've fixed it and pushed:

```bash
gh api graphql -f query='
mutation { resolveReviewThread(input: {threadId: "THREAD_ID"}) { thread { isResolved } } }'
```

## 6. Report

Tell the user: how many comments, how many fixed, how many pushed back on (and why),
anything still needing their decision, and the commit range you pushed.

## Running it automatically

To have this poll a PR and fix feedback as it arrives without being asked each time:

```
/loop 10m /address-review 112
```

That re-runs every ten minutes; it no-ops when there's nothing unresolved.
