"""
Typed configuration loaded from environment / .env file.
All secrets are resolved at import time — never hard-coded.
"""

from __future__ import annotations

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ── Gemini ────────────────────────────────────────────────────────────────
    gemini_api_key: str = Field(..., description="Google Gemini API key")
    gemini_model: str   = Field("gemini-2.0-flash", description="Gemini model id")

    # ── GitLab ────────────────────────────────────────────────────────────────
    gitlab_url: str        = Field("https://gitlab.com", description="GitLab base URL")
    gitlab_token: str      = Field(..., description="GitLab personal access token")
    gitlab_project_id: str = Field(..., description="GitLab project id or namespace/project")

    # ── Agent ─────────────────────────────────────────────────────────────────
    agent_max_tool_rounds: int   = Field(10,    ge=1,  le=30)
    agent_temperature: float     = Field(0.2,   ge=0.0, le=1.0)
    agent_verbose: bool          = Field(False)

    @field_validator("gitlab_url")
    @classmethod
    def strip_trailing_slash(cls, v: str) -> str:
        return v.rstrip("/")


# Singleton — import this everywhere
settings = Settings()  # type: ignore[call-arg]
