# Gemini MR Agent — Complete Design Document

> **Tool:** `tools/gemini-mr-agent/`
> **Language:** Python 3.11+
> **AI Model:** Google Gemini (function calling / agentic mode)
> **Target:** GitLab Merge Request API
> **Last Updated:** 2026-04-25

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Agentic Loop Design](#3-agentic-loop-design)
4. [Component Breakdown](#4-component-breakdown)
5. [File Structure](#5-file-structure)
6. [Configuration](#6-configuration)
7. [CLI Commands](#7-cli-commands)
8. [Tool Definitions (Function Calling)](#8-tool-definitions-function-calling)
9. [Agent Prompts](#9-agent-prompts)
10. [Authentication Flow](#10-authentication-flow)
11. [MR Description Template](#11-mr-description-template)
12. [Code Review Comment Template](#12-code-review-comment-template)
13. [Error Handling & Retry Strategy](#13-error-handling--retry-strategy)
14. [Security Considerations](#14-security-considerations)
15. [Installation & Quick Start](#15-installation--quick-start)
16. [Sequence Diagrams](#16-sequence-diagrams)
17. [Dependency Reference](#17-dependency-reference)

---

## 1. Overview

The **Gemini MR Agent** is a Python CLI tool that uses **Google Gemini's function-calling (agentic)** capability to:

1. Analyse a git branch diff against a target branch.
2. Generate a professional, detailed GitLab Merge Request title and description.
3. Perform an AI-powered code review of the diff.
4. Post structured review comments (general + inline) directly to the GitLab MR.

The agent operates autonomously — it decides which tools to call, in what order, and when it is done. The developer only provides the source/target branch; the agent handles everything else.

### What it replaces

| Manual task | Agent capability |
|---|---|
| Writing MR title/description | Auto-generated from git diff |
| Code review checklist | AI-generated findings table with severity levels |
| Inline comments | Posted directly to diff lines via GitLab API |
| Label selection | Agent queries available labels and picks appropriate ones |
| Risk assessment | Verdict + risk level (LOW / MEDIUM / HIGH / CRITICAL) |

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                       Developer Terminal                            │
│                                                                     │
│   gmr raise-mr --source feature/ad-auth --target main              │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CLI Layer  (cli.py)                              │
│   click commands: raise-mr | comment-mr | review-mr | config       │
│   Rich terminal output (panels, markdown rendering, progress)       │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  AgentInput(task, source, target, ...)
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  Agentic Loop  (agent.py)                           │
│                                                                     │
│   1. Build system prompt (task-specific)                            │
│   2. Send user message to Gemini                                    │
│   3. Receive response                                               │
│      ├─[function_call parts] → dispatch tools → feed results back  │
│      └─[text only]           → done, return final answer           │
│   4. Retry on transient errors (tenacity)                          │
└───────────┬──────────────────────────────┬──────────────────────────┘
            │                              │
            ▼                              ▼
┌───────────────────────┐    ┌─────────────────────────────────────┐
│   Google Gemini API   │    │        Tool Dispatcher  (tools.py)  │
│   (function calling)  │    │                                     │
│   gemini-2.0-flash    │    │  get_diff_summary()                 │
│   HS: AUTO mode       │    │  create_merge_request()             │
└───────────────────────┘    │  add_mr_comment()                   │
                             │  add_inline_comment()               │
                             │  list_open_mrs()                    │
                             │  get_mr_diff()                      │
                             │  + 5 more tools                     │
                             └──────────┬──────────────────────────┘
                                        │
                    ┌───────────────────┴────────────────────┐
                    │                                        │
                    ▼                                        ▼
       ┌────────────────────────┐            ┌──────────────────────────┐
       │   git_utils.py         │            │   gitlab_client.py       │
       │   (GitPython)          │            │   (python-gitlab)        │
       │                        │            │                          │
       │   get_diff_summary()   │            │   create_mr()            │
       │   get_current_branch() │            │   add_mr_note()          │
       │   get_branch_info()    │            │   add_inline_comment()   │
       │   get_repo_remote_url()│            │   get_mr_diff()          │
       └──────────┬─────────────┘            │   list_open_mrs()        │
                  │                          └────────────┬─────────────┘
                  ▼                                       ▼
       ┌────────────────────┐                ┌───────────────────────┐
       │  Local Git Repo    │                │  GitLab API           │
       │  (.git / branches) │                │  /api/v4/projects/... │
       └────────────────────┘                └───────────────────────┘
```

---

## 3. Agentic Loop Design

The agent uses **Gemini's AUTO function-calling mode**. Gemini decides which tools to invoke and when to stop.

```
Developer
    │
    │  gmr raise-mr
    ▼
AgentInput built
    │
    ▼
Gemini receives:
  - System prompt (task-specific instructions + output template)
  - User message  (source branch, target branch, extra instructions)
  - Tool declarations (11 function signatures)
    │
    ├─ Round 1 ──► function_call: get_current_branch()
    │              result: { "branch": "feature/ad-auth" }
    │
    ├─ Round 2 ──► function_call: get_diff_summary("feature/ad-auth", "main")
    │              result: { raw_diff, changed_files, stats, commit_messages }
    │
    ├─ Round 3 ──► function_call: get_project_labels()
    │              result: { labels: ["enhancement","security","bug",...] }
    │
    ├─ Round 4 ──► function_call: create_merge_request(
    │                title="feat: add AD authentication component",
    │                description="## Summary
...",
    │                labels="enhancement,security",
    │                target_branch="main"
    │              )
    │              result: { mr_iid: 42, url: "https://gitlab.com/.../42" }
    │
    ├─ Round 5 ──► function_call: add_mr_comment(
    │                mr_iid=42,
    │                comment="## MR Created ✅
..."
    │              )
    │              result: { note_id: 101, url: "..." }
    │
    └─ Final ────► text response: "MR !42 created successfully: ..."
                   (no more function_call parts → loop exits)
```

### Loop termination conditions

| Condition | Action |
|---|---|
| Response contains only text parts | Return final text — done |
| `agent_max_tool_rounds` reached | Return last text or raise |
| `KeyboardInterrupt` | Exit code 130 |
| Gemini API error (5xx) | Retry up to 3× with exponential backoff |
| GitLab API error | Return `{"error": "..."}` in tool result; Gemini decides to retry or abort |

---

## 4. Component Breakdown

### `config.py` — Settings

- Uses **pydantic-settings** (`BaseSettings`) for typed, validated configuration.
- Reads from `.env` file and environment variables (env takes precedence).
- All secrets (`GEMINI_API_KEY`, `GITLAB_TOKEN`) are required — startup fails clearly if missing.
- Module-level `settings` singleton imported by all other modules.

### `git_utils.py` — Git Operations

- Pure read-only operations on the local repository via **GitPython**.
- `get_diff_summary()` computes a three-dot diff (merge-base to source) — same as `git diff main...feature`.
- Raw diff is capped at **80 KB** before sending to Gemini to stay within context limits.
- No network calls — works entirely against local `.git`.

### `gitlab_client.py` — GitLab API Wrapper

- Wraps **python-gitlab** with dataclass return types (`MRResult`, `CommentResult`).
- Single authenticated project client (`GitLabClient`) instantiated once per run.
- Supports general comments (`add_mr_note`) and inline diff comments (`add_inline_comment`) with full position metadata.
- Module-level `get_client()` factory with lazy singleton pattern.

### `tools.py` — Function Calling Registry

- Each tool is a plain Python function with a descriptive docstring (Gemini reads the docstring as the tool description).
- `dispatch(name, args)` routes Gemini's function_call to the correct implementation.
- `GEMINI_TOOLS` list is the Gemini SDK tool declaration passed to `GenerativeModel`.
- Tools return `dict` → serialised to JSON string for the `FunctionResponse`.

### `agent.py` — Agentic Loop

- Builds the `GenerativeModel` with system prompt + tools + `GenerationConfig`.
- Runs the loop: send → collect `function_call` parts → dispatch all in parallel → feed `FunctionResponse` parts back → repeat.
- Verbose mode prints every tool call input/output as Rich syntax-highlighted panels.
- `@retry` decorator on `_send_to_gemini` handles transient 5xx errors.

### `cli.py` — CLI Entry Point

- **click** group with four commands: `raise-mr`, `comment-mr`, `review-mr`, `config`.
- **Rich** panels for input display, markdown rendering for agent output.
- Spinner progress bar during agent execution.
- `.env` loaded via `python-dotenv` before settings are imported.
- Exposed as the `gmr` console script via `pyproject.toml`.

---

## 5. File Structure

```
tools/
└── gemini-mr-agent/
    ├── pyproject.toml               ← Package config + dependencies + gmr entrypoint
    ├── .env.example                 ← Template for required secrets
    ├── .gitignore                   ← Excludes .env, __pycache__, .venv
    ├── README.md                    ← Quick-start guide
    └── gemini_agent/
        ├── __init__.py              ← Package marker + version
        ├── config.py                ← pydantic-settings: Settings dataclass
        ├── git_utils.py             ← GitPython wrappers: diff, branch, commits
        ├── gitlab_client.py         ← python-gitlab wrappers: MR CRUD, notes
        ├── tools.py                 ← 11 Gemini function-calling tool definitions
        ├── agent.py                 ← Gemini agentic loop + system prompts
        └── cli.py                   ← click CLI: raise-mr, comment-mr, review-mr, config
```

---

## 6. Configuration

All configuration is via environment variables or a `.env` file in the working directory.

```bash
cp .env.example .env
```

```env
# ─── Gemini ───────────────────────────────────────────────────────────────────
GEMINI_API_KEY=AIza...
GEMINI_MODEL=gemini-2.0-flash        # or gemini-1.5-pro

# ─── GitLab ───────────────────────────────────────────────────────────────────
GITLAB_URL=https://gitlab.com
GITLAB_TOKEN=glpat-...               # requires api scope
GITLAB_PROJECT_ID=123                # numeric id or namespace/project

# ─── Agent Behaviour ──────────────────────────────────────────────────────────
AGENT_MAX_TOOL_ROUNDS=10
AGENT_TEMPERATURE=0.2
AGENT_VERBOSE=false
```

### Settings Reference

| Variable | Type | Default | Description |
|---|---|---|---|
| `GEMINI_API_KEY` | string | required | Google AI Studio API key |
| `GEMINI_MODEL` | string | `gemini-2.0-flash` | Gemini model identifier |
| `GITLAB_URL` | string | `https://gitlab.com` | GitLab base URL |
| `GITLAB_TOKEN` | string | required | Personal access token (`api` scope) |
| `GITLAB_PROJECT_ID` | string | required | Numeric project id or `namespace/project` |
| `AGENT_MAX_TOOL_ROUNDS` | int | `10` | Max agentic loop iterations (1–30) |
| `AGENT_TEMPERATURE` | float | `0.2` | Gemini temperature (0.0–1.0) |
| `AGENT_VERBOSE` | bool | `false` | Print tool call traces to terminal |

---

## 7. CLI Commands

### `gmr raise-mr`

Analyse the current branch diff and create a GitLab MR with an AI-generated title and description.

```bash
# Minimal — detects current branch, targets main
gmr raise-mr

# Explicit branches
gmr raise-mr --source feature/ad-auth --target develop

# Draft MR with verbose tool traces
gmr raise-mr --draft --verbose

# Extra guidance to the agent
gmr raise-mr --instructions "This is a security hotfix, highlight risk in description"
```

**Options:**

| Option | Default | Description |
|---|---|---|
| `--source / -s` | current branch | Source branch to merge FROM |
| `--target / -t` | `main` | Target branch to merge INTO |
| `--draft` | false | Create as Draft MR |
| `--verbose / -v` | false | Show all tool inputs/outputs |
| `--repo PATH` | CWD | Path to git repository |
| `--instructions / -i` | — | Extra natural-language instructions |

---

### `gmr comment-mr`

Fetch an existing MR's diff and post a structured AI code-review comment.

```bash
# By MR IID
gmr comment-mr --mr 42

# By branch (agent finds the open MR)
gmr comment-mr --source feature/ad-auth

# Focused review
gmr comment-mr --mr 42 --instructions "Focus on LDAP injection risks only"
```

**Options:**

| Option | Default | Description |
|---|---|---|
| `--mr` | — | GitLab MR IID (`!n`) |
| `--source / -s` | current branch | Used to find MR when `--mr` is omitted |
| `--verbose / -v` | false | Show tool traces |
| `--instructions / -i` | — | Extra guidance (e.g. "focus on security") |

---

### `gmr review-mr`

Raise MR **and** post code review in a single agent pass.

```bash
gmr review-mr
gmr review-mr --source feature/ad-auth --target main --verbose
```

---

### `gmr config`

Validate settings and test API connectivity.

```bash
gmr config
```

Output:

```
┌─ Resolved Configuration ──────────────┐
│ Gemini Model      gemini-2.0-flash     │
│ Gemini API Key    AIza...zKey          │
│ GitLab URL        https://gitlab.com   │
│ GitLab Token      glpa...t123          │
│ GitLab Project ID 123                  │
│ Max Tool Rounds   10                   │
│ Temperature       0.2                  │
└────────────────────────────────────────┘

Testing GitLab connectivity…  ✓ GitLab connection OK
Testing Gemini connectivity…  ✓ Gemini connection OK
```

---

## 8. Tool Definitions (Function Calling)

The agent has access to **11 tools**. Gemini autonomously decides which to call and when.

### Git Tools (read-only, local repo)

| Tool | Arguments | Returns |
|---|---|---|
| `get_current_branch` | `repo_path` | `{ branch }` |
| `get_branch_info` | `branch, repo_path` | `{ name, remote, tracking, ahead, behind }` |
| `get_diff_summary` | `source_branch, target_branch, repo_path` | `{ raw_diff, changed_files, stats, commit_messages }` |
| `get_repo_remote_url` | `repo_path` | `{ remote_url }` |

### GitLab Tools (network, GitLab API)

| Tool | Arguments | Returns |
|---|---|---|
| `list_open_mrs` | `source_branch` | `{ merge_requests: [{mr_iid, title, url, state}] }` |
| `get_mr_diff` | `mr_iid` | `{ diff }` |
| `get_mr_commits` | `mr_iid` | `{ commits: [{sha, title, author}] }` |
| `create_merge_request` | `source_branch, target_branch, title, description, labels, draft, squash` | `{ mr_iid, title, url, state }` |
| `add_mr_comment` | `mr_iid, comment` | `{ note_id, url }` |
| `add_inline_comment` | `mr_iid, comment, file_path, new_line, base_sha, head_sha, start_sha` | `{ note_id, url }` |
| `get_project_labels` | — | `{ labels: [string] }` |

### Tool dispatch flow

```
Gemini returns FunctionCall { name: "get_diff_summary", args: {...} }
       │
       ▼
tools.dispatch("get_diff_summary", args)
       │
       ▼
tool_get_diff_summary(source_branch, target_branch, repo_path)
       │
       ├── git_utils.get_diff_summary(...)  →  DiffSummary dataclass
       │
       └── json.dumps(result)  →  returned as FunctionResponse to Gemini
```

---

## 9. Agent Prompts

Three system prompts — one per task. All are defined in `agent.py`.

### raise-mr prompt (key excerpt)

```
You are an expert software engineer and technical writer acting as a GitLab MR Agent.

Workflow:
1. Call get_current_branch() if source branch is unknown.
2. Call get_diff_summary(source_branch, target_branch).
3. Call get_project_labels() to know which labels exist.
4. Analyse the diff: modules changed, type of change, motivation.
5. Call create_merge_request() with:
   - Concise imperative title (max 72 chars), type-prefixed:
     feat: | fix: | refactor: | docs: | chore: | security: | test:
   - Detailed markdown description (see template).
   - Appropriate labels.
6. Post a structured summary comment via add_mr_comment().
```

### comment-mr prompt (key excerpt)

```
You are a senior code reviewer with expertise in:
Java / Spring Boot / Spring Security / PKI / LDAP / MariaDB / OWASP Top 10.

Workflow:
1. Fetch MR diff via get_mr_diff(mr_iid).
2. Fetch commit history via get_mr_commits(mr_iid).
3. Review across dimensions:
   a. Security (injection, auth bypass, secrets)
   b. Correctness (logic bugs, null handling, edge cases)
   c. Performance (N+1, missing indexes)
   d. Design (SOLID, over-engineering)
   e. Tests (coverage, weak assertions)
4. Post ONE comprehensive comment via add_mr_comment() with:
   - Verdict: APPROVE / REQUEST CHANGES / COMMENT
   - Risk Level: LOW / MEDIUM / HIGH / CRITICAL
   - Findings table + detailed sections + positive observations
5. Post targeted inline comments for critical findings.
```

### review-mr prompt

Combines both prompts: agent first creates the MR, then reviews it.

---

## 10. Authentication Flow

```
Developer runs: gmr raise-mr
       │
       ▼
config.py loads .env
       ├── GEMINI_API_KEY  → passed to genai.configure()
       └── GITLAB_TOKEN    → passed to gitlab.Gitlab(private_token=...)
       │
       ▼
LdapConnectionConfig (not applicable here — direct token auth)
       │
       ▼
GitLabClient.__init__()
       ├── gitlab.Gitlab(url, private_token=token)
       ├── gl.auth()                  ← validates token, fetches current user
       └── gl.projects.get(project_id)  ← validates project access
       │
       ▼
agent.py _build_model()
       └── genai.configure(api_key=settings.gemini_api_key)
           GenerativeModel(model_name, system_instruction, tools, ...)
       │
       ▼
Agent loop runs — all subsequent calls use the authenticated instances
```

**GitLab token scopes required:**

| Scope | Required for |
|---|---|
| `api` | Full access: create MR, post comments, list labels |
| `read_api` | Read-only (comment-mr only) |
| `write_repository` | Not required (MRs are created via API, not git push) |

---

## 11. MR Description Template

The agent uses this template when generating MR descriptions (embedded in the system prompt).

```markdown
## Summary
<!-- 2-3 sentence overview of what this MR does and why -->

## Changes
<!-- Bullet list grouped by module/layer, derived from actual diff -->
- **Layer / Module**: what changed and why

## Motivation & Context
<!-- Why was this change needed? Link to issue/ticket if available -->

## Testing
- [ ] Unit tests added / updated
- [ ] Integration tests passing
- [ ] Manual smoke test performed
- [ ] No regressions in existing tests

## Security Considerations
<!-- Any security impact: auth changes, data exposure, secret handling -->
<!-- If none: "No security impact identified" -->

## Database / Migration
<!-- Schema changes, new tables, migration scripts needed -->
<!-- If none: "No database changes" -->

## Checklist
- [ ] Code follows project style guide
- [ ] No hardcoded secrets or credentials
- [ ] Logging is appropriate (no PII in logs)
- [ ] Error handling is complete
- [ ] API documentation updated if endpoints changed
- [ ] CHANGELOG updated
```

### MR title format

```
{type}: {short imperative description}   (max 72 chars)

Types:
  feat:      New feature
  fix:       Bug fix
  refactor:  Code restructuring (no behaviour change)
  docs:      Documentation only
  test:      Test additions/changes
  chore:     Build, config, dependency updates
  security:  Security fix or hardening
  perf:      Performance improvement
  ci:        CI/CD changes

Examples:
  feat: add Active Directory multi-domain authentication
  security: prevent LDAP injection in username sanitisation
  fix: handle AD server timeout with fallback DC
```

---

## 12. Code Review Comment Template

The agent uses this structure when posting review comments.

```markdown
## Code Review — {MR Title}

### Verdict: [APPROVE | REQUEST CHANGES | COMMENT]
**Risk Level:** [LOW | MEDIUM | HIGH | CRITICAL]

---

### Summary
2-3 sentence overall assessment of the change quality, completeness,
and readiness to merge.

---

### Findings

| # | Severity | File | Line | Finding |
|---|---|---|---|---|
| 1 | 🔴 Critical | AdUserDetailsService.java | 42 | LDAP injection via unsanitised input |
| 2 | 🟠 High     | TokenService.java         | 80 | JWT secret derived from weak entropy |
| 3 | 🟡 Medium   | SecurityConfig.java        | 15 | CSRF disabled globally, not scoped |
| 4 | 🟢 Low      | LdapConnectionConfig.java  | 5  | Unused import |

---

### Detailed Review

#### 🔴 Security
**Finding 1 — LDAP Injection (Critical)**
File: `AdUserDetailsService.java:42`

The `username` parameter is used directly in the LDAP search filter without
sanitisation:
```java
// UNSAFE
String filter = "(&(objectClass=user)(sAMAccountName=" + username + "))";
```
An attacker can inject `)(|(cn=*)` to bypass authentication.

**Fix:**
```java
// SAFE — use Spring LDAP parameterised filter
String filter = "(&(objectClass=user)(sAMAccountName={0}))";
ldapTemplate.search(query().filter(filter, username), ...);
```

---

#### 🟠 Correctness
...

#### 🟡 Performance
...

#### 🟢 Design & Maintainability
...

---

#### ✅ What's Done Well
- Bind password resolved from Vault reference — never stored in config.
- JWT uses HS512 with a strong key from `TokenService`.
- Audit logging is async (`@Async`) — does not slow the auth critical path.
- `objectGuid` used as stable AD identity anchor — survives username renames.

---

### Suggested Actions

**Blocking (must fix before merge):**
1. Sanitise username in LDAP search filter (finding #1).
2. Derive JWT secret from Vault, not from `application.yml` (finding #2).

**Non-blocking (recommended):**
3. Scope CSRF exclusion to `/auth/**` only (finding #3).
4. Remove unused import (finding #4).
```

### Severity scale

| Icon | Level | Meaning |
|---|---|---|
| 🔴 | Critical | Security vulnerability or data corruption — blocks merge |
| 🟠 | High | Correctness bug or significant security risk — blocks merge |
| 🟡 | Medium | Performance issue or design concern — should fix |
| 🟢 | Low | Style, naming, minor improvement — optional |
| ✅ | Positive | Good practice worth calling out |

---

## 13. Error Handling & Retry Strategy

### Gemini API errors

```python
@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    reraise=True,
)
def _send_to_gemini(chat, message):
    return chat.send_message(message)
```

| Error type | Behaviour |
|---|---|
| `ResourceExhausted` (429) | Retry up to 3× with 2s → 4s → 10s backoff |
| `ServiceUnavailable` (503) | Retry up to 3× |
| `InvalidArgument` (400) | Raise immediately (bad prompt/config) |
| `PermissionDenied` (403) | Raise immediately (bad API key) |

### GitLab API errors

Tool functions catch all exceptions and return `{"error": "..."}` JSON.
Gemini receives the error in the `FunctionResponse` and can decide to retry,
try an alternative tool, or report the failure in its final answer.

| Error | Tool response | Gemini behaviour |
|---|---|---|
| 401 Unauthorized | `{"error": "401 Unauthorized"}` | Reports config issue in final answer |
| 404 Not Found (MR) | `{"error": "404 Not Found"}` | Tries `list_open_mrs` as fallback |
| 409 Conflict (MR exists) | `{"error": "..."}` | Reports existing MR URL |
| 422 Unprocessable | `{"error": "..."}` | Adjusts parameters and retries |
| Network timeout | `{"error": "timeout"}` | Reports connectivity issue |

### CLI exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Agent or API error |
| 130 | Interrupted by Ctrl+C |

---

## 14. Security Considerations

| Concern | Mitigation |
|---|---|
| **API keys in .env** | `.env` in `.gitignore`; never committed to version control |
| **Diff sent to Gemini** | Diff content is sent to Google's API — review data-handling policy for sensitive repos |
| **GitLab token scope** | Use minimum required scope; prefer project-scoped tokens |
| **LDAP injection** | Usernames sanitised in `git_utils.py` before any LDAP filter construction |
| **Tool input validation** | All tool functions validate/coerce inputs before calling external APIs |
| **Bind password** | Stored in HashiCorp Vault; `bind_secret_ref` is a key name, never the password |
| **Local git access** | Read-only GitPython calls; no `git push` or write operations |
| **Inline comment SHAs** | base/head/start SHAs must come from GitLab MR diff version — not user-supplied |

---

## 15. Installation & Quick Start

### Prerequisites

- Python 3.11+
- Git (local repository)
- GitLab Personal Access Token with `api` scope
- Google AI Studio API key (Gemini)

### Install

```bash
cd tools/gemini-mr-agent

# Create virtual environment
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate

# Install package + dependencies
pip install -e .

# Verify installation
gmr --version
```

### Configure

```bash
cp .env.example .env
# Edit .env with your keys
gmr config                          # validates connectivity
```

### First run

```bash
# On a feature branch with commits
git checkout feature/my-feature

# Raise an MR
gmr raise-mr --target main

# Review MR !5
gmr comment-mr --mr 5

# Do both at once
gmr review-mr --target main --verbose
```

---

## 16. Sequence Diagrams

### raise-mr full sequence

```
Developer         CLI            Agent Loop        Gemini            Git          GitLab
    |               |                |               |                |              |
    |--gmr raise-mr>|                |               |                |              |
    |               |--AgentInput--->|               |                |              |
    |               |                |--build model->|                |              |
    |               |                |--send msg---->|                |              |
    |               |                |               |--tool_call---->|              |
    |               |                |               | get_current_   |              |
    |               |                |               | branch()       |              |
    |               |                |<-fn_call------|                |              |
    |               |                |--dispatch()-->|                |              |
    |               |                |               |--git branch--->|              |
    |               |                |               |<--"feature/x"--|              |
    |               |                |--fn_response->|                |              |
    |               |                |               |--tool_call: get_diff_summary  |
    |               |                |<-fn_call------|                |              |
    |               |                |--dispatch()-->|--git diff----->|              |
    |               |                |               |<--DiffSummary--|              |
    |               |                |--fn_response->|                |              |
    |               |                |               |--tool_call: get_project_labels|
    |               |                |<-fn_call------|                |              |
    |               |                |--dispatch()---------------------------------->|
    |               |                |               |                |<--[labels]---|
    |               |                |--fn_response->|                |              |
    |               |                |               |--tool_call: create_mr-------->|
    |               |                |<-fn_call------|                |              |
    |               |                |--dispatch()---------------------------------->|
    |               |                |               |                |<--MRResult---|
    |               |                |--fn_response->|                |              |
    |               |                |               |--tool_call: add_mr_comment--->|
    |               |                |<-fn_call------|                |              |
    |               |                |--dispatch()---------------------------------->|
    |               |                |               |                |<--NoteResult-|
    |               |                |--fn_response->|                |              |
    |               |                |               |--[text only]-->|              |
    |               |                |<-final text---|                |              |
    |               |<-result--------|               |                |              |
    |<-Rich panel---|                |               |                |              |
```

### comment-mr full sequence

```
Developer         CLI            Agent Loop        Gemini          GitLab
    |               |                |               |                |
    |--gmr comment  |                |               |                |
    |  --mr 42----->|                |               |                |
    |               |--AgentInput--->|               |                |
    |               |                |--send msg---->|                |
    |               |                |               |--get_mr_diff(42)----------->|
    |               |                |<-fn_call------|                |            |
    |               |                |--dispatch()------------------------------->|
    |               |                |               |                |<--diff-----|
    |               |                |--fn_response->|                |            |
    |               |                |               |--get_mr_commits(42)-------->|
    |               |                |<-fn_call------|                |            |
    |               |                |--dispatch()------------------------------->|
    |               |                |               |                |<-commits---|
    |               |                |--fn_response->|                |            |
    |               |                |               |  [Gemini analyses diff]     |
    |               |                |               |--add_mr_comment(42, review)->
    |               |                |<-fn_call------|                |            |
    |               |                |--dispatch()------------------------------->|
    |               |                |               |                |<-note_id---|
    |               |                |--fn_response->|                |            |
    |               |                |               |--[text only]-->|            |
    |               |                |<-final text---|                |            |
    |               |<-result--------|               |                |            |
    |<-Rich panel---|                |               |                |            |
```

---

## 17. Dependency Reference

```toml
# pyproject.toml dependencies

google-generativeai>=0.8.3   # Gemini SDK with function calling
python-gitlab>=4.9.0         # GitLab REST API client
gitpython>=3.1.43            # Local git repository access
click>=8.1.7                 # CLI framework
rich>=13.7.1                 # Terminal output (panels, markdown, progress)
python-dotenv>=1.0.1         # .env file loading
pydantic>=2.7.1              # Data validation
pydantic-settings>=2.3.1     # Typed settings from env vars
tenacity>=8.3.0              # Retry logic with exponential backoff
```

### Version compatibility

| Dependency | Min Version | Reason |
|---|---|---|
| `google-generativeai` | 0.8.3 | Function calling + `FunctionResponse` part API |
| `python-gitlab` | 4.9.0 | MR discussions API (`add_inline_comment`) |
| `gitpython` | 3.1.43 | `merge_base()` method |
| `pydantic-settings` | 2.3.1 | `SettingsConfigDict` |
| Python | 3.11 | `match` statement, `tomllib`, type hints |

---

*Gemini MR Agent — PKI-RA Project | Python 3.11+ | Google Gemini | GitLab API v4*
