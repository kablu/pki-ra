"""
Thin wrapper around python-gitlab.
All GitLab API calls go through this module — keeps agent tools clean.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

import gitlab
from gitlab.v4.objects import Project, MergeRequest

from .config import settings


# ── data classes ─────────────────────────────────────────────────────────────

@dataclass
class MRResult:
    mr_id: int
    mr_iid: int
    title: str
    description: str
    url: str
    source_branch: str
    target_branch: str
    state: str


@dataclass
class CommentResult:
    note_id: int
    url: str
    body: str


# ── client ────────────────────────────────────────────────────────────────────

class GitLabClient:
    """Authenticated GitLab client scoped to a single project."""

    def __init__(self) -> None:
        self._gl = gitlab.Gitlab(
            url=settings.gitlab_url,
            private_token=settings.gitlab_token,
            retry_transient_errors=True,
            timeout=30,
        )
        self._gl.auth()
        self._project: Project = self._gl.projects.get(settings.gitlab_project_id)

    # ── MR operations ────────────────────────────────────────────────────────

    def create_mr(
        self,
        source_branch: str,
        target_branch: str,
        title: str,
        description: str,
        labels: list[str] | None = None,
        assignee_ids: list[int] | None = None,
        remove_source_on_merge: bool = True,
        squash: bool = False,
        draft: bool = False,
    ) -> MRResult:
        """Create a new merge request. Raises if one already exists for the branch pair."""
        mr_title = f"Draft: {title}" if draft else title

        payload: dict = {
            "source_branch":              source_branch,
            "target_branch":              target_branch,
            "title":                      mr_title,
            "description":                description,
            "remove_source_branch":       remove_source_on_merge,
            "squash":                     squash,
        }
        if labels:
            payload["labels"] = ",".join(labels)
        if assignee_ids:
            payload["assignee_ids"] = assignee_ids

        mr: MergeRequest = self._project.mergerequests.create(payload)
        return self._to_result(mr)

    def get_mr(self, mr_iid: int) -> MRResult:
        mr = self._project.mergerequests.get(mr_iid)
        return self._to_result(mr)

    def list_open_mrs(self, source_branch: str | None = None) -> list[MRResult]:
        filters = {"state": "opened"}
        if source_branch:
            filters["source_branch"] = source_branch
        mrs = self._project.mergerequests.list(**filters, all=True)
        return [self._to_result(m) for m in mrs]

    def get_mr_diff(self, mr_iid: int) -> str:
        """Return the unified diff for an existing MR."""
        mr   = self._project.mergerequests.get(mr_iid)
        diffs = mr.diffs.list(all=True)
        if not diffs:
            return ""
        # Use the latest diff version
        latest = diffs[-1]
        detail = mr.diffs.get(latest.id)
        lines: list[str] = []
        for d in detail.diffs:
            lines.append(f"--- {d['old_path']}")
            lines.append(f"+++ {d['new_path']}")
            lines.append(d.get("diff", ""))
        return "\n".join(lines)

    # ── Comment operations ────────────────────────────────────────────────────

    def add_mr_note(self, mr_iid: int, body: str) -> CommentResult:
        """Add a general (non-inline) comment to an MR."""
        mr   = self._project.mergerequests.get(mr_iid)
        note = mr.notes.create({"body": body})
        url  = f"{settings.gitlab_url}/{self._project.path_with_namespace}/-/merge_requests/{mr_iid}#note_{note.id}"
        return CommentResult(note_id=note.id, url=url, body=body)

    def add_inline_comment(
        self,
        mr_iid: int,
        body: str,
        file_path: str,
        new_line: int,
        base_sha: str,
        head_sha: str,
        start_sha: str,
    ) -> CommentResult:
        """Add an inline diff comment at a specific line."""
        mr = self._project.mergerequests.get(mr_iid)
        discussion = mr.discussions.create({
            "body": body,
            "position": {
                "position_type":  "text",
                "base_sha":        base_sha,
                "head_sha":        head_sha,
                "start_sha":       start_sha,
                "new_path":        file_path,
                "old_path":        file_path,
                "new_line":        new_line,
            },
        })
        note_id = discussion.attributes["notes"][0]["id"]
        url = (
            f"{settings.gitlab_url}/{self._project.path_with_namespace}"
            f"/-/merge_requests/{mr_iid}#note_{note_id}"
        )
        return CommentResult(note_id=note_id, url=url, body=body)

    def get_mr_commits(self, mr_iid: int) -> list[dict]:
        mr = self._project.mergerequests.get(mr_iid)
        return [
            {"sha": c.id[:8], "title": c.title, "author": c.author_name}
            for c in mr.commits()
        ]

    # ── helpers ──────────────────────────────────────────────────────────────

    def get_project_labels(self) -> list[str]:
        return [lb.name for lb in self._project.labels.list(all=True)]

    def get_project_members(self) -> list[dict]:
        return [
            {"id": m.id, "username": m.username, "name": m.name}
            for m in self._project.members.list(all=True)
        ]

    @staticmethod
    def _to_result(mr: MergeRequest) -> MRResult:
        return MRResult(
            mr_id=mr.id,
            mr_iid=mr.iid,
            title=mr.title,
            description=mr.description or "",
            url=mr.web_url,
            source_branch=mr.source_branch,
            target_branch=mr.target_branch,
            state=mr.state,
        )


# Module-level singleton (lazy)
_client: GitLabClient | None = None


def get_client() -> GitLabClient:
    global _client
    if _client is None:
        _client = GitLabClient()
    return _client
