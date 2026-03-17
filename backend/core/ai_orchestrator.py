"""
AI Orchestrator — model-agnostic intelligence layer.

This is the architectural decision that future-proofs InsightLenz.
When a better AI model ships tomorrow, you change one env variable.
Zero code changes. Every user gets the upgrade automatically.

Supported providers: Anthropic (Claude), OpenAI (GPT), Google (Gemini)
Active provider: controlled by ACTIVE_AI_PROVIDER in .env
"""

import structlog
from typing import AsyncGenerator
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings
from core.context_engine import ContextEngine

log = structlog.get_logger()


class Message(object):
    def __init__(self, role: str, content: str):
        self.role = role
        self.content = content


class AIResponse(object):
    def __init__(self, content: str, model: str, provider: str, tokens_used: int = 0):
        self.content = content
        self.model = model
        self.provider = provider
        self.tokens_used = tokens_used


class AIOrchestrator:
    """
    Single interface to any AI provider.

    The rest of InsightLenz never calls a specific AI provider directly.
    Everything goes through here. Swap providers by changing .env.
    """

    def __init__(self, context_engine: ContextEngine):
        self.context_engine = context_engine
        self.provider = settings.active_ai_provider
        self.model = settings.active_ai_model
        log.info("ai_orchestrator_initialized", provider=self.provider, model=self.model)

    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10)
    )
    async def think(
        self,
        user_message: str,
        conversation_history: list[Message] = None,
        inject_full_context: bool = True,
    ) -> AIResponse:
        """
        Core method. Send a message, get a response.
        Full user context is injected automatically unless disabled.
        """
        system_prompt = (
            self.context_engine.build_system_prompt()
            if inject_full_context
            else "You are InsightLenz, a personal AI operating system."
        )

        history = conversation_history or []

        if self.provider == "anthropic":
            return await self._call_anthropic(system_prompt, user_message, history)
        elif self.provider == "openai":
            return await self._call_openai(system_prompt, user_message, history)
        elif self.provider == "google":
            return await self._call_google(system_prompt, user_message, history)
        else:
            raise ValueError(f"Unknown AI provider: {self.provider}")

    async def think_about_decision(self, decision: str) -> AIResponse:
        """
        Specialised call for decision support.
        Injects decision-specific context on top of full user context.
        """
        decision_context = self.context_engine.get_decision_context(decision)
        prompt = f"""
{decision_context}

The user is facing this decision. Run it through their values, priorities,
and non-negotiables. Be direct about what aligns and what doesn't.
Give a clear recommendation with your reasoning.

Do not hedge. Do not give generic advice. This person knows you know them.
"""
        return await self.think(prompt, inject_full_context=True)

    async def generate_morning_brief(self) -> AIResponse:
        """
        Generate the morning ritual brief — proactive, not reactive.
        Called by the workflow engine at the user's morning trigger time.
        """
        prompt = """Generate the morning brief for today. Include:

1. ONE THING — the single most important outcome for today (based on their priorities)
2. THREE TASKS — the three specific actions that would make today successful
3. WATCH OUT — one risk or pattern that might hijack their day today
4. QUICK WIN — one small thing they can do in the first 30 minutes to build momentum

Be specific. Reference their actual projects and priorities.
No generic advice. This should feel like a briefing from someone who knows them."""

        return await self.think(prompt)

    async def generate_insight(self, trigger: str) -> AIResponse:
        """
        Proactively generate an insight based on something InsightLenz noticed.
        This is what makes InsightLenz feel alive — it reaches out to you.
        """
        prompt = f"""
You've noticed something worth surfacing: {trigger}

Generate a proactive insight for the user. This is not a response to a question —
this is InsightLenz noticing something and deciding the user should know.

Keep it tight: one observation, one implication, one suggested action.
Don't pad it. If it's not genuinely useful, say nothing.
"""
        return await self.think(prompt)

    # ── Provider implementations ─────────────────────────────────────────────

    async def _call_anthropic(
        self,
        system: str,
        user_message: str,
        history: list[Message]
    ) -> AIResponse:
        import anthropic
        client = anthropic.AsyncAnthropic(api_key=settings.anthropic_api_key)

        messages = [
            {"role": m.role, "content": m.content}
            for m in history
        ]
        messages.append({"role": "user", "content": user_message})

        response = await client.messages.create(
            model=self.model,
            max_tokens=2048,
            system=system,
            messages=messages,
        )

        return AIResponse(
            content=response.content[0].text,
            model=self.model,
            provider="anthropic",
            tokens_used=response.usage.input_tokens + response.usage.output_tokens,
        )

    async def _call_openai(
        self,
        system: str,
        user_message: str,
        history: list[Message]
    ) -> AIResponse:
        from openai import AsyncOpenAI
        client = AsyncOpenAI(api_key=settings.openai_api_key)

        messages = [{"role": "system", "content": system}]
        messages += [{"role": m.role, "content": m.content} for m in history]
        messages.append({"role": "user", "content": user_message})

        response = await client.chat.completions.create(
            model=self.model,
            messages=messages,
            max_tokens=2048,
        )

        return AIResponse(
            content=response.choices[0].message.content,
            model=self.model,
            provider="openai",
            tokens_used=response.usage.total_tokens,
        )

    async def _call_google(
        self,
        system: str,
        user_message: str,
        history: list[Message]
    ) -> AIResponse:
        import google.generativeai as genai
        genai.configure(api_key=settings.google_api_key)
        model = genai.GenerativeModel(
            model_name=self.model,
            system_instruction=system,
        )

        chat = model.start_chat(history=[
            {"role": m.role, "parts": [m.content]}
            for m in history
        ])
        response = await chat.send_message_async(user_message)

        return AIResponse(
            content=response.text,
            model=self.model,
            provider="google",
            tokens_used=0,  # Gemini doesn't always return token counts
        )
