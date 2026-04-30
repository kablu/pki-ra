"""
Gemini agentic loop.

Pattern:
  1. Build a system prompt describing the task.
  2. Send the initial message to Gemini.
  3. If Gemini returns function_call parts → dispatch them, feed results back.
  4. Repeat until Gemini returns a plain text response (no more tool calls).
  5. Return the final text.

The agent has access to all tools in tools.py and decides the execution order.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from enum import Enum
from typing import Optional

import google.generativeai as genai
from rich.console import Console
from rich.markdown import Markdown
from rich.panel import Panel
from rich.progress import Progress, SpinnerColumn, TextColumn
from rich.syntax import Syntax
from tenacity import retry, stop_after_attempt, wait_exponential

from .config import settings
from .tools import GEMINI_TOOLS, dispatch

console = Console()


class AgentTask(str, Enum):
    RAISE_MR = "raise_mr"
    COMMENT_MR = "comment_mr"
    REVIEW_MR = "review_mr"   # raise + comment in one pass


@dataclass
class AgentInput:
    task: AgentTask
    source_branch: Optional[str] = None
    target_branch: Optional[str] = None
    mr_iid: Optional[int] = None
    repo_path: Optional[str] = None
    extra_instructions: Optional[str] = None


# ── System prompts ────────────────────────────────────────────────────────────

_SYSTEM_RAISE_MR = """\
You are an expert software engineer and technical writer acting as a GitLab MR Agent.

Your goal: create a high-quality GitLab Merge Request for the given branch changes.

## Workflow
1. Call get_current_branch() if source branch is unknown.
2. Call get_diff_summary(source_branch, target_branch) to understand the changes.
3. Call get_project_labels() to know which labels exist.
4. Analyse the diff carefully:
   - What modules/files changed?
   - What is the type of change? (feat, fix, refactor, chore, docs, test, security)
   - What is the business/technical motivation?
5. Call create_merge_request() with:
   - A concise imperative title (max 72 chars), prefixed with type:
     feat:, fix:, refactor:, docs:, chore:, security:, test:
   - A detailed markdown description (see template below).
   - Appropriate labels.
6. After creation, post a structured summary comment via add_mr_comment().

## MR Description Template
```
## Summary
<!-- 2-3 sentence overview of what this MR does and why -->

## Changes
<!-- Bullet list grouped by module/layer -->
- **Layer/Module**: what changed and why

## Motivation & Context
<!-- Why was this change needed? Link to issue/ticket if available -->

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests passing
- [ ] Manual smoke test performed

## Security Considerations
<!-- Any security impact: auth changes, data exposure, secret handling -->

## Database / Migration
<!-- Any schema changes, new tables, migrations needed -->

## Checklist
- [ ] Code follows project style guide
- [ ] No hardcoded secrets or credentials
- [ ] Logging is appropriate (no PII in logs)
- [ ] Error handling is complete
- [ ] Documentation updated if needed
```

Be factual — base the MR entirely on the actual diff content.
"""

_SYSTEM_COMMENT_MR = """\
You are a senior code reviewer with deep expertise in:
- Java / Spring Boot / Spring Security
- PKI, X.509 certificates, LDAP / Active Directory
- Database design (MariaDB / JPA)
- Security (OWASP Top 10, secure coding)
- Clean code, SOLID principles

Your goal: review the given GitLab MR thoroughly and post detailed comments.

## Workflow
1. Call list_open_mrs() or use the provided mr_iid.
2. Call get_mr_diff(mr_iid) to read all changes.
3. Call get_mr_commits(mr_iid) to understand the change history.
4. Perform a deep review:
   a. Security issues (injection, auth bypass, insecure deserialization, secrets)
   b. Correctness (logic bugs, null handling, edge cases)
   c. Performance (N+1 queries, missing indexes, unnecessary computation)
   d. Design (SOLID violations, over-engineering, missing abstractions)
   e. Test coverage (missing tests, weak assertions)
   f. Documentation (missing Javadoc, unclear variable names)
5. Post ONE comprehensive summary comment via add_mr_comment() with:
   - Overall verdict: APPROVE / REQUEST CHANGES / COMMENT
   - Risk level: LOW / MEDIUM / HIGH / CRITICAL
   - Findings table
   - Detailed sections per concern
   - Positive observations (what is done well)
6. Optionally post targeted inline comments for the most critical findings.

## Comment Structure
```
## Code Review — [MR Title]

### Verdict: [APPROVE | REQUEST CHANGES | COMMENT]
**Risk Level:** [LOW | MEDIUM | HIGH | CRITICAL]

---

### Summary
[2-3 sentence overall assessment]

---

### Findings

| # | Severity | File | Finding |
|---|---|---|---|
| 1 | 🔴 Critical | file.java:42 | ... |
| 2 | 🟠 High     | file.java:80 | ... |
| 3 | 🟡 Medium   | file.java:15 | ... |
| 4 | 🟢 Low      | file.java:5  | ... |

---

### Detailed Review

#### 🔴 Security
[specific findings with code references]

#### 🟠 Correctness
[specific findings]

#### 🟡 Performance
[specific findings]

#### 🟢 Design & Maintainability
[specific findings]

#### ✅ What's Done Well
[positive observations — always include this section]

---

### Suggested Actions
1. [Blocking] Fix ...
2. [Non-blocking] Consider ...
```

Be specific: quote file names and line numbers. Use code blocks for examples.
Provide fix suggestions, not just problems.
"""

_SYSTEM_REVIEW_MR = (
    _SYSTEM_RAISE_MR.replace(
        "Your goal: create a high-quality GitLab Merge Request",
        "Your goal: FIRST create a GitLab Merge Request, THEN review it",
    )
    + "\n\nAfter creating the MR, perform a full code review following these guidelines:\n\n"
    + _SYSTEM_COMMENT_MR.split("Your goal:")[1]
)


def _system_prompt(task: AgentTask) -> str:
    mapping = {
        AgentTask.RAISE_MR:   _SYSTEM_RAISE_MR,
        AgentTask.COMMENT_MR: _SYSTEM_COMMENT_MR,
        AgentTask.REVIEW_MR:  _SYSTEM_REVIEW_MR,
    }
    return mapping[task]


# ── Gemini model setup ────────────────────────────────────────────────────────

def _build_model(task: AgentTask) -> genai.GenerativeModel:
    genai.configure(api_key=settings.gemini_api_key)
    return genai.GenerativeModel(
        model_name=settings.gemini_model,
        system_instruction=_system_prompt(task),
        tools=GEMINI_TOOLS,
        generation_config=genai.GenerationConfig(
            temperature=settings.agent_temperature,
            candidate_count=1,
        ),
        tool_config=genai.protos.ToolConfig(
            function_calling_config=genai.protos.FunctionCallingConfig(
                mode=genai.protos.FunctionCallingConfig.Mode.AUTO,
            )
        ),
    )


# ── User message builder ──────────────────────────────────────────────────────

def _build_user_message(inp: AgentInput) -> str:
    parts: list[str] = []

    if inp.task == AgentTask.RAISE_MR:
        src = inp.source_branch or "(detect automatically)"
        tgt = inp.target_branch or "main"
        parts.append(
            f"Please create a GitLab MR to merge **{src}** into **{tgt}**.\n"
            "Use the tools to analyse the diff and generate a thorough MR description."
        )

    elif inp.task == AgentTask.COMMENT_MR:
        if inp.mr_iid:
            parts.append(
                f"Please perform a detailed code review of GitLab MR **!{inp.mr_iid}**.\n"
                "Fetch the diff, analyse it thoroughly, and post a structured review comment."
            )
        else:
            src = inp.source_branch or "(detect automatically)"
            parts.append(
                f"Find the open MR for branch **{src}** and perform a detailed code review.\n"
                "Post a structured review comment."
            )

    elif inp.task == AgentTask.REVIEW_MR:
        src = inp.source_branch or "(detect automatically)"
        tgt = inp.target_branch or "main"
        parts.append(
            f"Please: (1) create a GitLab MR to merge **{src}** into **{tgt}**, "
            "then (2) perform a detailed code review of the new MR and post a "
            "structured comment."
        )

    if inp.repo_path:
        parts.append(f"\nRepository path: `{inp.repo_path}`")

    if inp.extra_instructions:
        parts.append(f"\nAdditional instructions: {inp.extra_instructions}")

    return "\n".join(parts)


# ── Agentic loop ──────────────────────────────────────────────────────────────

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    reraise=True,
)
def _send_to_gemini(
    chat: genai.ChatSession,
    message: str | list,
) -> genai.types.GenerateContentResponse:
    return chat.send_message(message)


def run_agent(inp: AgentInput) -> str:
    """
    Execute the agentic loop for the given task.
    Returns the final text response from Gemini.
    """
    model = _build_model(inp.task)
    chat  = model.start_chat(enable_automatic_function_calling=False)

    user_msg = _build_user_message(inp)

    if settings.agent_verbose:
        console.print(Panel(user_msg, title="[bold cyan]User Message", border_style="cyan"))

    response    = _send_to_gemini(chat, user_msg)
    round_count = 0

    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        console=console,
        transient=True,
    ) as progress:
        task_id = progress.add_task("[cyan]Agent thinking…", total=None)

        while round_count < settings.agent_max_tool_rounds:
            # Collect all function_call parts from this response
            tool_calls = [
                part.function_call
                for candidate in response.candidates
                for part in candidate.content.parts
                if part.function_call.name
            ]

            if not tool_calls:
                # No more tool calls — Gemini is done
                break

            round_count += 1
            tool_results: list[genai.protos.Part] = []

            for call in tool_calls:
                fn_name = call.name
                fn_args = dict(call.args)

                progress.update(
                    task_id,
                    description=f"[cyan]Tool [{round_count}]: [bold]{fn_name}[/bold]",
                )

                if settings.agent_verbose:
                    console.print(
                        Panel(
                            Syntax(json.dumps(fn_args, indent=2), "json", theme="monokai"),
                            title=f"[bold yellow]→ {fn_name}",
                            border_style="yellow",
                        )
                    )

                result_json = dispatch(fn_name, fn_args)

                if settings.agent_verbose:
                    console.print(
                        Panel(
                            Syntax(result_json[:2000], "json", theme="monokai"),
                            title=f"[bold green]← {fn_name}",
                            border_style="green",
                        )
                    )

                tool_results.append(
                    genai.protos.Part(
                        function_response=genai.protos.FunctionResponse(
                            name=fn_name,
                            response={"result": result_json},
                        )
                    )
                )

            # Feed all results back in one turn
            response = _send_to_gemini(chat, tool_results)

    # Extract final text
    final_text = ""
    for candidate in response.candidates:
        for part in candidate.content.parts:
            if part.text:
                final_text += part.text

    return final_text.strip()
