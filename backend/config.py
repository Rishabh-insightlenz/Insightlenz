from pydantic_settings import BaseSettings
from pydantic import field_validator, model_validator
from typing import Literal


# Default model for each provider — used when ACTIVE_AI_MODEL is not explicitly set
_PROVIDER_DEFAULT_MODELS = {
    "anthropic": "claude-sonnet-4-6",
    "openai":    "gpt-4o",
    "google":    "gemini-1.5-pro",
    "groq":      "llama-3.3-70b-versatile",
}


class Settings(BaseSettings):
    # App
    app_env: str = "development"
    app_secret_key: str = "dev-secret-change-in-production"
    debug: bool = True

    # Database
    database_url: str = "postgresql+asyncpg://insightlenz:password@localhost:5432/insightlenz"

    @field_validator("database_url", mode="before")
    @classmethod
    def normalize_database_url(cls, v: str) -> str:
        """Render injects DATABASE_URL as postgres:// or postgresql://.
        SQLAlchemy + asyncpg requires postgresql+asyncpg://."""
        if isinstance(v, str):
            if v.startswith("postgres://"):
                return v.replace("postgres://", "postgresql+asyncpg://", 1)
            if v.startswith("postgresql://") and "+asyncpg" not in v:
                return v.replace("postgresql://", "postgresql+asyncpg://", 1)
        return v

    # AI — fully model-agnostic, swap without touching any other code
    active_ai_provider: Literal["anthropic", "openai", "google", "groq"] = "anthropic"
    active_ai_model: str = ""   # empty = auto-select based on provider (see validator below)
    anthropic_api_key: str = ""
    openai_api_key: str = ""
    google_api_key: str = ""
    groq_api_key: str = ""

    @model_validator(mode="after")
    def configure_provider(self) -> "Settings":
        """1. Auto-select correct model if ACTIVE_AI_MODEL is not explicitly set.
        2. Fall back to any provider that actually has an API key configured —
           prevents 500s when ACTIVE_AI_PROVIDER is set but the key is missing."""
        api_keys = {
            "anthropic": self.anthropic_api_key,
            "openai":    self.openai_api_key,
            "google":    self.google_api_key,
            "groq":      self.groq_api_key,
        }

        # If the configured provider has no key, fall back to whichever provider does
        if not api_keys.get(self.active_ai_provider):
            for provider, key in api_keys.items():
                if key:
                    self.active_ai_provider = provider
                    self.active_ai_model = _PROVIDER_DEFAULT_MODELS[provider]
                    return self

        # Provider has a key — just ensure model is set correctly
        if not self.active_ai_model:
            self.active_ai_model = _PROVIDER_DEFAULT_MODELS.get(
                self.active_ai_provider, "llama-3.3-70b-versatile"
            )
        return self

    # Voice
    whisper_model: str = "base"

    # InsightLenz
    max_memory_items: int = 1000
    insight_generation_interval_minutes: int = 60

    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()
