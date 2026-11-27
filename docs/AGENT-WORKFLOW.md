# Agent workflow

How AI coding agents work in this repository. The rules themselves are in [AGENTS.md](../AGENTS.md); this page
covers the tooling around them.

## Local sessions (Claude Code)

- `.claude/settings.json` is committed. It denies reading secret files and force pushes, and runs
  `scripts/agent-guard.mjs` before every file edit and shell command (see AGENTS.md, "Working with AI agents").
- Put personal overrides in `.claude/settings.local.json` (git-ignored). Session worktrees go under
  `.claude/worktrees/` (git-ignored).
- `/verify` runs the checks AGENTS.md requires and reports what was and was not verified.
- Use one worktree per agent so two agents never edit the same files.

## Issue to pull request (opt-in)

[`agent-issue.yml`](../.github/workflows/agent-issue.yml) runs Claude Code on one issue and opens a pull request.
It does nothing until the secret below exists.

**Start it** (owner only): add the `agent` label to an issue, or run the workflow by hand with the issue number
(`gh workflow run agent-issue.yml -f issue=123`).

**What it does**: reads the issue, works on a `fix/issue-N` or `feature/issue-N` branch, adds tests, runs the checks
from AGENTS.md, opens a PR that says what was and was not tested, and stops. It never merges. If the issue is a
product decision or too vague, it comments with questions instead of guessing.

### One-time setup (owner)

1. Create a Claude Code OAuth token (`claude setup-token` in a terminal) and store it as the repository secret
   `CLAUDE_CODE_OAUTH_TOKEN`. It uses the subscription, so it adds no per-token API cost, but runs count against the
   subscription limits.
2. Create the `agent` label if it does not exist.

### Known limits

- **CI does not run on the agent's PR by itself.** A pull request opened with the workflow's `GITHUB_TOKEN` does not
  trigger other workflows. Push an empty commit, close and reopen the PR, or re-run the checks by hand before
  reviewing. Using a personal access token would fix this, but it would hand a wider credential to a process that
  reads issue text, so it is deliberately not done.
- **No emulator and no iOS** on the runner: the PR must list those as not tested, and emulator QA stays a manual pass.
- **Android builds are slow on a hosted runner**; the run is capped at 60 minutes and 60 turns.
- **Prompt injection.** The issue text is the agent's input. Mitigations in place: only the owner can start a run,
  the run fails unless the issue was **written by the owner** (a stranger's issue cannot reach the agent, and others
  cannot edit it), the agent is told not to read comments and to treat the issue as data, its tools are an
  allow-list (no `rm`, `curl` or free-form shell), the guard hook and the deny rules apply, and `.github/workflows`,
  release files and Doppler are off limits by instruction.
- **Residual risk: the agent runs code.** `./gradlew`, `node` and `npm` are allowed so it can run the checks, and
  they execute whatever the repository (and the agent's own edits) contain. A successful injection could therefore
  read the job's environment, which holds `CLAUDE_CODE_OAUTH_TOKEN` and the job's `GITHUB_TOKEN`, and the runner has
  network access. Accept this only for issues you wrote yourself, keep the token scoped to this one purpose, and
  revoke and recreate it (`claude setup-token`) if a run ever behaves oddly.
- **`main` is only partly protected.** The ruleset "Protect default branch" blocks deleting `main` and force
  pushes, but does **not** require a pull request, so a token with `contents: write` can still push to `main`. The
  guard hook blocks it locally and in this workflow, but a ruleset rule "Require a pull request before merging" is the
  real fix and is the owner's call (Settings, Rules). It also makes the owner's own direct pushes to `main` impossible,
  which is why it is not enabled here.

## Other agents already in place

- Dependabot opens the dependency PRs (`.github/dependabot.yml`). They are reviewed like any other PR.
- `/code-review` in a local session reviews a diff before you push.
