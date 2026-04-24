# GMR — Gemini MR Agent

AI-powered CLI agent that uses **Google Gemini** to automatically raise
GitLab Merge Requests and post detailed code-review comments.

---

## Architecture

```
gmr CLI (click)
   └── AgentInput
         └── run_agent()  ← Gemini agentic loop (agent.py)
               ├── Tool: get_diff_summary()    → git_utils.py → local repo
               ├── Tool: create_merge_request() → gitlab_client.py → GitLab API
               ├── Tool: add_mr_comment()       → gitlab_client.py → GitLab API
               └── ...                          (11 tools total)
```

### Agentic Loop

```
User invokes CLI
      │
      ▼
Gemini receives task + system prompt
      │
      ├─[tool_call: get_diff_summary] ──► git diff ──► result back to Gemini
      │
      ├─[tool_call: get_project_labels] ─► GitLab ──► result back to Gemini
      │
      ├─[tool_call: create_merge_request] ► GitLab ──► MR URL back to Gemini
      │
      └─[tool_call: add_mr_comment] ──────► GitLab ──► note_id back to Gemini
            │
            └─ Gemini returns final summary text → printed to terminal
```

---

## Installation

```bash
cd tools/gemini-mr-agent

# Create virtual environment
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate

# Install in editable mode
pip install -e .
```

---

## Configuration

```bash
cp .env.example .env
```

Edit `.env`:

```env
GEMINI_API_KEY=AIza...
GEMINI_MODEL=gemini-2.0-flash

GITLAB_URL=https://gitlab.com
GITLAB_TOKEN=glpat-...
GITLAB_PROJECT_ID=123
```

Verify:

```bash
gmr config
```

---

## Usage

### Raise a Merge Request

```bash
# From current branch → main (auto-detects branch)
gmr raise-mr

# Explicit branches
gmr raise-mr --source feature/ad-auth --target develop

# Create as draft
gmr raise-mr --draft

# With extra instructions to the agent
gmr raise-mr --instructions "This is a security hotfix — highlight risk in the description"
```

### Review an Existing MR

```bash
# By MR IID
gmr comment-mr --mr 42

# By branch (agent finds the open MR)
gmr comment-mr --source feature/ad-auth

# Focus the review
gmr comment-mr --mr 42 --instructions "Focus on LDAP injection risks"
```

### Raise + Review in One Command

```bash
gmr review-mr
gmr review-mr --source feature/ad-auth --target main --verbose
```

### Show Configuration

```bash
gmr config
```

---

## Commands

| Command | Description |
|---|---|
| `gmr raise-mr` | Analyse diff → create GitLab MR with AI title + description |
| `gmr comment-mr` | Fetch MR diff → post structured AI code review comment |
| `gmr review-mr` | Raise MR + post review comment in one pass |
| `gmr config` | Validate settings and test connectivity |

### Global Flags

| Flag | Description |
|---|---|
| `--verbose / -v` | Show all tool call inputs and outputs |
| `--repo PATH` | Path to git repo (default: CWD) |
| `--instructions TEXT` | Extra natural-language instructions for the agent |

---

## What the Agent Generates

### MR Description (raise-mr)

- **Title** — imperative mood, type-prefixed (`feat:`, `fix:`, `security:` …)
- **Summary** — 2-3 sentence overview
- **Changes** — bullet list grouped by module/layer, derived from the actual diff
- **Motivation & Context** — why this change was needed
- **Testing checklist** — unit, integration, smoke
- **Security Considerations** — auth changes, secret handling
- **Database / Migration** — schema changes detected from diff
- **Merge checklist** — style, no hardcoded secrets, logging, docs

### Code Review Comment (comment-mr)

- **Verdict** — APPROVE / REQUEST CHANGES / COMMENT
- **Risk Level** — LOW / MEDIUM / HIGH / CRITICAL
- **Findings table** — severity, file, description for every finding
- **Detailed sections** — Security, Correctness, Performance, Design, Tests
- **Positive observations** — what is done well (always included)
- **Suggested actions** — blocking vs non-blocking items with fix examples

---

## Available Tools (Gemini function calling)

| Tool | Description |
|---|---|
| `get_current_branch` | Get checked-out branch name |
| `get_branch_info` | Branch upstream tracking / ahead-behind |
| `get_diff_summary` | Three-dot diff, changed files, commit messages |
| `get_repo_remote_url` | Origin remote URL |
| `list_open_mrs` | List open GitLab MRs (optionally by branch) |
| `get_mr_diff` | Fetch unified diff for an existing MR |
| `get_mr_commits` | List commits in an MR |
| `create_merge_request` | Create a GitLab MR |
| `add_mr_comment` | Post a general MR comment |
| `add_inline_comment` | Post an inline diff comment at a specific line |
| `get_project_labels` | List available GitLab labels |

---

## Security Notes

- `.env` is in `.gitignore` — never commit tokens.
- `GITLAB_TOKEN` requires `api` scope (or `read_api` + `write_repository`).
- `GEMINI_API_KEY` is only used locally; diff content is sent to Google's API.
- For sensitive repos, review Google's data usage policy before use.
