"""
Git operations used by the Gemini agent tools.
All reads are performed against the local repository.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import git


# ── helpers ───────────────────────────────────────────────────────────────────

def _repo(path: str | None = None) -> git.Repo:
    """Return a Repo for *path* or the CWD, walking up to find .git."""
    start = Path(path).resolve() if path else Path.cwd()
    return git.Repo(start, search_parent_directories=True)


# ── public API ────────────────────────────────────────────────────────────────

@dataclass
class BranchInfo:
    name: str
    remote: Optional[str]
    tracking: Optional[str]
    ahead: int
    behind: int


@dataclass
class DiffSummary:
    source_branch: str
    target_branch: str
    raw_diff: str
    changed_files: list[str]
    stats: str          # e.g. "12 files changed, 340 insertions(+), 45 deletions(-)"
    commit_messages: list[str]


def get_current_branch(repo_path: str | None = None) -> str:
    repo = _repo(repo_path)
    return repo.active_branch.name


def get_branch_info(branch: str, repo_path: str | None = None) -> BranchInfo:
    repo = _repo(repo_path)
    try:
        b = repo.branches[branch]  # type: ignore[index]
    except IndexError:
        raise ValueError(f"Branch '{branch}' not found in local repository")

    remote_name: str | None = None
    tracking: str | None = None
    ahead = behind = 0

    if b.tracking_branch():
        tb = b.tracking_branch()
        remote_name = tb.remote_name
        tracking    = tb.name
        commits_behind = list(repo.iter_commits(f"{branch}..{tracking}"))
        commits_ahead  = list(repo.iter_commits(f"{tracking}..{branch}"))
        ahead  = len(commits_ahead)
        behind = len(commits_behind)

    return BranchInfo(
        name=branch,
        remote=remote_name,
        tracking=tracking,
        ahead=ahead,
        behind=behind,
    )


def get_diff_summary(
    source_branch: str,
    target_branch: str,
    repo_path: str | None = None,
    max_diff_bytes: int = 80_000,
) -> DiffSummary:
    """
    Compute the diff between *source_branch* and *target_branch*.

    The raw diff is truncated at *max_diff_bytes* so it fits within
    the Gemini context window.
    """
    repo = _repo(repo_path)

    source_commit = repo.commit(source_branch)
    target_commit = repo.commit(target_branch)

    # Three-dot diff: changes introduced by source relative to merge base
    merge_base = repo.merge_base(target_commit, source_commit)
    if not merge_base:
        raise ValueError(f"No common ancestor between {source_branch} and {target_branch}")

    base_commit = merge_base[0]
    diff_index  = base_commit.diff(source_commit)

    changed_files: list[str] = [d.a_path or d.b_path for d in diff_index]

    # Raw unified diff (git diff base..source)
    raw = repo.git.diff(
        f"{base_commit.hexsha}..{source_commit.hexsha}",
        unified=5,
    )
    if len(raw.encode()) > max_diff_bytes:
        raw = raw.encode()[:max_diff_bytes].decode(errors="replace") + "\n\n[DIFF TRUNCATED]"

    # Stat line
    stats = repo.git.diff(
        f"{base_commit.hexsha}..{source_commit.hexsha}",
        stat=True,
    ).strip().split("\n")[-1]

    # Commit messages between base and source
    commits = list(repo.iter_commits(f"{base_commit.hexsha}..{source_commit.hexsha}"))
    messages = [f"- {c.hexsha[:8]} {c.summary}" for c in commits]

    return DiffSummary(
        source_branch=source_branch,
        target_branch=target_branch,
        raw_diff=raw,
        changed_files=changed_files,
        stats=stats,
        commit_messages=messages,
    )


def get_repo_remote_url(repo_path: str | None = None) -> str | None:
    """Return the origin remote URL, or None if not set."""
    try:
        repo = _repo(repo_path)
        return repo.remotes["origin"].url
    except (IndexError, git.InvalidGitRepositoryError):
        return None
