"""
CLI entry point — exposed as the `gmr` command after `pip install -e .`

Commands:
  gmr raise-mr   — create a GitLab MR from the current branch
  gmr comment-mr — post an AI code-review comment on an existing MR
  gmr review-mr  — raise MR AND post code-review comment in one shot
  gmr config     — validate configuration and print resolved settings
"""

from __future__ import annotations

import sys
from pathlib import Path

import click
from dotenv import load_dotenv
from rich.console import Console
from rich.markdown import Markdown
from rich.panel import Panel
from rich.table import Table

# Load .env before importing settings
load_dotenv(dotenv_path=Path.cwd() / ".env", override=False)

from .agent import AgentInput, AgentTask, run_agent  # noqa: E402
from .config import settings                          # noqa: E402

console = Console()
err     = Console(stderr=True)


# ── shared options ────────────────────────────────────────────────────────────

_verbose_option = click.option(
    "--verbose", "-v",
    is_flag=True,
    default=False,
    help="Show tool call traces during agent execution.",
)

_repo_option = click.option(
    "--repo",
    default="",
    metavar="PATH",
    help="Path to the git repository (default: current directory).",
)

_extra_option = click.option(
    "--instructions", "-i",
    default="",
    metavar="TEXT",
    help="Extra natural-language instructions passed to the agent.",
)


# ── main group ────────────────────────────────────────────────────────────────

@click.group()
@click.version_option("1.0.0", prog_name="gmr")
def main() -> None:
    """
    \b
    GMR — Gemini MR Agent
    AI-powered GitLab Merge Request creation and code review.

    \b
    Quick start:
      cp .env.example .env       # fill in GEMINI_API_KEY, GITLAB_TOKEN, etc.
      gmr raise-mr               # create MR from current branch → main
      gmr comment-mr --mr 42     # review MR !42
      gmr review-mr              # raise + review in one command
    """


# ── raise-mr ─────────────────────────────────────────────────────────────────

@main.command("raise-mr")
@click.option("--source", "-s", default="", metavar="BRANCH",
              help="Source branch (default: current branch).")
@click.option("--target", "-t", default="main", show_default=True, metavar="BRANCH",
              help="Target branch to merge into.")
@click.option("--draft", is_flag=True, default=False,
              help="Create the MR as a draft.")
@_verbose_option
@_repo_option
@_extra_option
def raise_mr(
    source: str,
    target: str,
    draft: bool,
    verbose: bool,
    repo: str,
    instructions: str,
) -> None:
    """
    Analyse the current branch diff and create a GitLab MR with
    an AI-generated title and detailed description.

    \b
    Examples:
      gmr raise-mr
      gmr raise-mr --source feature/ad-auth --target develop
      gmr raise-mr --draft --verbose
    """
    if verbose:
        settings.agent_verbose = True

    extra = instructions
    if draft:
        extra = f"Create the MR as a Draft. {extra}".strip()

    console.print(
        Panel(
            f"[bold]Source:[/bold] {source or '(current branch)'}\n"
            f"[bold]Target:[/bold] {target}",
            title="[cyan]Raise MR",
            border_style="cyan",
        )
    )

    _run_and_print(
        AgentInput(
            task=AgentTask.RAISE_MR,
            source_branch=source or None,
            target_branch=target,
            repo_path=repo or None,
            extra_instructions=extra or None,
        )
    )


# ── comment-mr ───────────────────────────────────────────────────────────────

@main.command("comment-mr")
@click.option("--mr", "mr_iid", type=int, default=None, metavar="MR_IID",
              help="GitLab MR IID (!n). If omitted, agent finds it by source branch.")
@click.option("--source", "-s", default="", metavar="BRANCH",
              help="Source branch to look up the MR (used when --mr is not given).")
@_verbose_option
@_repo_option
@_extra_option
def comment_mr(
    mr_iid: int | None,
    source: str,
    verbose: bool,
    repo: str,
    instructions: str,
) -> None:
    """
    Perform a detailed AI code review on an existing GitLab MR
    and post a structured comment.

    \b
    Examples:
      gmr comment-mr --mr 42
      gmr comment-mr --source feature/ad-auth
      gmr comment-mr --mr 42 --instructions "Focus on security issues only"
    """
    if verbose:
        settings.agent_verbose = True

    if mr_iid is None and not source:
        # Try to use current branch
        from .git_utils import get_current_branch
        try:
            source = get_current_branch(repo or None)
        except Exception:
            err.print("[red]Error:[/red] Provide --mr or --source (or run from a git repo).")
            sys.exit(1)

    target_desc = f"MR !{mr_iid}" if mr_iid else f"MR for branch '{source}'"
    console.print(
        Panel(
            f"[bold]Target:[/bold] {target_desc}",
            title="[cyan]Comment MR",
            border_style="cyan",
        )
    )

    _run_and_print(
        AgentInput(
            task=AgentTask.COMMENT_MR,
            source_branch=source or None,
            mr_iid=mr_iid,
            repo_path=repo or None,
            extra_instructions=instructions or None,
        )
    )


# ── review-mr ────────────────────────────────────────────────────────────────

@main.command("review-mr")
@click.option("--source", "-s", default="", metavar="BRANCH",
              help="Source branch (default: current branch).")
@click.option("--target", "-t", default="main", show_default=True, metavar="BRANCH",
              help="Target branch to merge into.")
@click.option("--draft", is_flag=True, default=False,
              help="Create the MR as a draft.")
@_verbose_option
@_repo_option
@_extra_option
def review_mr(
    source: str,
    target: str,
    draft: bool,
    verbose: bool,
    repo: str,
    instructions: str,
) -> None:
    """
    Create a GitLab MR AND post a detailed code review comment — all in one shot.

    \b
    Example:
      gmr review-mr --source feature/ad-auth --target develop
    """
    if verbose:
        settings.agent_verbose = True

    extra = instructions
    if draft:
        extra = f"Create the MR as a Draft. {extra}".strip()

    console.print(
        Panel(
            f"[bold]Source:[/bold] {source or '(current branch)'}\n"
            f"[bold]Target:[/bold] {target}\n"
            f"[bold]Mode:[/bold]   Raise MR + Code Review",
            title="[cyan]Review MR",
            border_style="cyan",
        )
    )

    _run_and_print(
        AgentInput(
            task=AgentTask.REVIEW_MR,
            source_branch=source or None,
            target_branch=target,
            repo_path=repo or None,
            extra_instructions=extra or None,
        )
    )


# ── config ───────────────────────────────────────────────────────────────────

@main.command("config")
def show_config() -> None:
    """Validate and display the resolved configuration (secrets are masked)."""
    table = Table(title="Resolved Configuration", border_style="cyan")
    table.add_column("Setting",  style="bold")
    table.add_column("Value")

    def mask(v: str) -> str:
        if len(v) <= 8:
            return "***"
        return v[:4] + "..." + v[-4:]

    table.add_row("Gemini Model",        settings.gemini_model)
    table.add_row("Gemini API Key",      mask(settings.gemini_api_key))
    table.add_row("GitLab URL",          settings.gitlab_url)
    table.add_row("GitLab Token",        mask(settings.gitlab_token))
    table.add_row("GitLab Project ID",   str(settings.gitlab_project_id))
    table.add_row("Max Tool Rounds",     str(settings.agent_max_tool_rounds))
    table.add_row("Temperature",         str(settings.agent_temperature))
    table.add_row("Verbose",             str(settings.agent_verbose))

    console.print(table)

    # Verify GitLab connectivity
    console.print("\n[cyan]Testing GitLab connectivity…[/cyan]")
    try:
        from .gitlab_client import get_client
        client = get_client()
        console.print("[green]✓ GitLab connection OK[/green]")
    except Exception as exc:
        err.print(f"[red]✗ GitLab connection failed:[/red] {exc}")

    # Verify Gemini connectivity
    console.print("[cyan]Testing Gemini connectivity…[/cyan]")
    try:
        import google.generativeai as genai
        genai.configure(api_key=settings.gemini_api_key)
        m = genai.GenerativeModel(settings.gemini_model)
        m.generate_content("ping", generation_config={"max_output_tokens": 5})
        console.print("[green]✓ Gemini connection OK[/green]")
    except Exception as exc:
        err.print(f"[red]✗ Gemini connection failed:[/red] {exc}")


# ── helper ────────────────────────────────────────────────────────────────────

def _run_and_print(inp: AgentInput) -> None:
    try:
        result = run_agent(inp)
        console.print("\n")
        console.print(
            Panel(
                Markdown(result),
                title="[bold green]Agent Result",
                border_style="green",
            )
        )
    except KeyboardInterrupt:
        err.print("\n[yellow]Interrupted by user.[/yellow]")
        sys.exit(130)
    except Exception as exc:
        err.print(f"\n[red]Agent error:[/red] {exc}")
        if settings.agent_verbose:
            console.print_exception()
        sys.exit(1)


if __name__ == "__main__":
    main()
