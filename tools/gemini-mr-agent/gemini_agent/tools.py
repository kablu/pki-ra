"""
Gemini function-calling tool definitions.

Each function here is:
  1. Declared as a Python callable with a clear docstring.
  2. Registered with the Gemini SDK via genai.protos.Tool.
  3. Dispatched inside the agentic loop (agent.py).

The agent decides which tools to call and in what order.
"""

from __future__ import annotations

import json
from typing import Any

import google.generativeai as genai

from .git_utils import (
    get_current_branch,
    get_branch_info,
    get_diff_summary,
    get_repo_remote_url,
)
from .gitlab_client import get_client


# ═════════════════════════════════════════════════════════════════════════════
#  Tool implementations
# ═════════════════════════════════════════════════════════════════════════════

def tool_get_current_branch(repo_path: str = "") -> dict:
    """Return the currently checked-out branch name."""
    try:
        branch = get_current_branch(repo_path or None)
        return {"branch": branch}
    except Exception as exc:
        return {"error": str(exc)}


def tool_get_branch_info(branch: str, repo_path: str = "") -> dict:
    """
    Return metadata for a local branch: remote tracking info,
    how many commits it is ahead/behind its upstream.
    """
    try:
        info = get_branch_info(branch, repo_path or None)
        return {
            "name":     info.name,
            "remote":   info.remote,
            "tracking": info.tracking,
            "ahead":    info.ahead,
            "behind":   info.behind,
        }
    except Exception as exc:
        return {"error": str(exc)}


def tool_get_diff_summary(
    source_branch: str,
    target_branch: str,
    repo_path: str = "",
) -> dict:
    """
    Return the git diff between source_branch and target_branch
    (three-dot diff relative to merge base), including:
    - raw_diff: full unified diff (may be truncated at 80 KB)
    - changed_files: list of file paths modified
    - stats: summary line e.g. "5 files changed, 120 insertions(+)"
    - commit_messages: list of commits on source not in target
    """
    try:
        summary = get_diff_summary(source_branch, target_branch, repo_path or None)
        return {
            "source_branch":    summary.source_branch,
            "target_branch":    summary.target_branch,
            "raw_diff":         summary.raw_diff,
            "changed_files":    summary.changed_files,
            "stats":            summary.stats,
            "commit_messages":  summary.commit_messages,
        }
    except Exception as exc:
        return {"error": str(exc)}


def tool_get_repo_remote_url(repo_path: str = "") -> dict:
    """Return the origin remote URL of the local repository."""
    url = get_repo_remote_url(repo_path or None)
    return {"remote_url": url or ""}


def tool_list_open_mrs(source_branch: str = "") -> dict:
    """
    List open MRs in the GitLab project.
    Optionally filter by source_branch.
    Returns list of {mr_iid, title, url, state}.
    """
    try:
        client = get_client()
        mrs = client.list_open_mrs(source_branch or None)
        return {
            "merge_requests": [
                {
                    "mr_iid":        m.mr_iid,
                    "title":         m.title,
                    "url":           m.url,
                    "state":         m.state,
                    "source_branch": m.source_branch,
                    "target_branch": m.target_branch,
                }
                for m in mrs
            ]
        }
    except Exception as exc:
        return {"error": str(exc)}


def tool_get_mr_diff(mr_iid: int) -> dict:
    """
    Fetch the unified diff for an existing GitLab MR.
    Use this when you need to review an existing MR's changes.
    """
    try:
        client = get_client()
        diff   = client.get_mr_diff(mr_iid)
        return {"diff": diff}
    except Exception as exc:
        return {"error": str(exc)}


def tool_get_mr_commits(mr_iid: int) -> dict:
    """Return the list of commits in a GitLab MR."""
    try:
        client  = get_client()
        commits = client.get_mr_commits(mr_iid)
        return {"commits": commits}
    except Exception as exc:
        return {"error": str(exc)}


def tool_create_merge_request(
    source_branch: str,
    target_branch: str,
    title: str,
    description: str,
    labels: str = "",
    draft: bool = False,
    squash: bool = False,
) -> dict:
    """
    Create a GitLab Merge Request.

    Args:
        source_branch: Branch to merge FROM.
        target_branch: Branch to merge INTO (e.g. main, develop).
        title:         Concise MR title (imperative mood, max 72 chars).
        description:   Full markdown description:
                       - Summary section
                       - What changed (bullet list)
                       - Why / motivation
                       - Testing notes
                       - Checklist
        labels:        Comma-separated label names (optional).
        draft:         If true, create as Draft MR.
        squash:        If true, set squash-on-merge.

    Returns dict with mr_iid, title, url on success.
    """
    try:
        client = get_client()
        label_list = [l.strip() for l in labels.split(",") if l.strip()] if labels else []
        result = client.create_mr(
            source_branch=source_branch,
            target_branch=target_branch,
            title=title,
            description=description,
            labels=label_list,
            draft=draft,
            squash=squash,
        )
        return {
            "mr_iid":   result.mr_iid,
            "title":    result.title,
            "url":      result.url,
            "state":    result.state,
        }
    except Exception as exc:
        return {"error": str(exc)}


def tool_add_mr_comment(mr_iid: int, comment: str) -> dict:
    """
    Post a general (non-inline) comment on a GitLab MR.
    Use this for overall review summaries, approval notes,
    or change requests that span the entire MR.

    The comment should be detailed markdown — include:
    - Overall assessment
    - Specific observations per file/section
    - Security, performance, or correctness concerns
    - Suggested improvements with code snippets where helpful
    """
    try:
        client = get_client()
        result = client.add_mr_note(mr_iid, comment)
        return {"note_id": result.note_id, "url": result.url}
    except Exception as exc:
        return {"error": str(exc)}


def tool_add_inline_comment(
    mr_iid: int,
    comment: str,
    file_path: str,
    new_line: int,
    base_sha: str,
    head_sha: str,
    start_sha: str,
) -> dict:
    """
    Post an inline diff comment on a specific line of a GitLab MR.
    Use this for precise, line-level feedback.

    Args:
        mr_iid:     MR internal ID (iid).
        comment:    Markdown comment for this specific line.
        file_path:  Relative file path (e.g. src/main/java/Foo.java).
        new_line:   Line number in the new version of the file.
        base_sha:   base commit SHA from the MR diff version.
        head_sha:   head commit SHA from the MR diff version.
        start_sha:  start commit SHA from the MR diff version.
    """
    try:
        client = get_client()
        result = client.add_inline_comment(
            mr_iid=mr_iid,
            body=comment,
            file_path=file_path,
            new_line=new_line,
            base_sha=base_sha,
            head_sha=head_sha,
            start_sha=start_sha,
        )
        return {"note_id": result.note_id, "url": result.url}
    except Exception as exc:
        return {"error": str(exc)}


def tool_get_project_labels() -> dict:
    """List all labels defined in the GitLab project."""
    try:
        labels = get_client().get_project_labels()
        return {"labels": labels}
    except Exception as exc:
        return {"error": str(exc)}


# ═════════════════════════════════════════════════════════════════════════════
#  Tool registry — maps function name → callable + Gemini declaration
# ═════════════════════════════════════════════════════════════════════════════

TOOL_FUNCTIONS: dict[str, Any] = {
    "get_current_branch":    tool_get_current_branch,
    "get_branch_info":       tool_get_branch_info,
    "get_diff_summary":      tool_get_diff_summary,
    "get_repo_remote_url":   tool_get_repo_remote_url,
    "list_open_mrs":         tool_list_open_mrs,
    "get_mr_diff":           tool_get_mr_diff,
    "get_mr_commits":        tool_get_mr_commits,
    "create_merge_request":  tool_create_merge_request,
    "add_mr_comment":        tool_add_mr_comment,
    "add_inline_comment":    tool_add_inline_comment,
    "get_project_labels":    tool_get_project_labels,
}


def dispatch(function_name: str, args: dict) -> str:
    """Dispatch a tool call from the agent loop and return a JSON string result."""
    fn = TOOL_FUNCTIONS.get(function_name)
    if fn is None:
        return json.dumps({"error": f"Unknown tool: {function_name}"})
    result = fn(**args)
    return json.dumps(result, ensure_ascii=False, default=str)


# ── Gemini tool declaration (passed to genai.GenerativeModel) ────────────────

GEMINI_TOOLS = [
    genai.protos.Tool(
        function_declarations=[
            genai.protos.FunctionDeclaration(
                name=name,
                description=fn.__doc__ or "",
                parameters=genai.protos.Schema(type=genai.protos.Type.OBJECT),
            )
            for name, fn in TOOL_FUNCTIONS.items()
        ]
    )
]
