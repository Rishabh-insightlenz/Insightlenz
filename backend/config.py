from pydantic_settings import BaseSettings
from typing import Literal


class Settings(BaseSettings):
    # App
    app_env: str = "development"
    app_secret_key: str = "dev-secret-change-in-production"
    debug: bool = True

    # Database
    database_url: str = "postgresql+asyncpg://insightlenz:password@localhost:5432/insightlenz"
    # AI — fully model-agnostic, swap without touching any other code
    active_ai_provider: Literal["anthropic", "openai", "google", "groq"] = "anthropic"
    active_ai_model: str = "claude-sonnet-4-6"
    anthropic_api_key: str = ""
    openai_api_key: str = ""
    google_api_key: str = ""
    groq_api_key: str = ""

    # Voice
    whisper_model: str = "base"

    # InsightLenz
    max_memory_items: int = 1000
    insight_generation_interval_minutes: int = 60

    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()
