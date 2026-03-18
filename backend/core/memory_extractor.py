"""
Memory Extractor — InsightLenz learns from every conversation.

After each chat exchange, this runs silently in the background.
It reads what was said and extracts anything worth remembering:
decisions, commitments, patterns, wins, misses.

Over time, InsightLenz stops relying on what you explicitly told it
and starts knowing things it observed. That's the difference between
a profile and a relationship.
"""

import json
import structlog
from uuid import UUID

from models.memory import Memory, MemoryType

log = structlog.get_logger()


# The extraction prompt is intentionally lean — we want structured output,
# not a verbose analysis. Keep token usage minimal since this runs after
# every single chat message.
EXTRACTION_PROMPT = """Extract memorable facts from this conversation exchange. Return JSON only.

USER: {user_message}
ASSISTANT: {assistant_response}

Return a JSON array of memory objects. Only include things genuinely worth remembering long-term.
Skip routine greetings, simple factual questions, or anything already in the user's profile.

Each object must have:
  "type": one of "decision" | "commitment" | "insight" | "pattern" | "win" | "miss"
  "content": what happened or was said, 1-2 sentences
  "context": why it's worth remembering, 1 sentence
  "tags": array of 1-3 short keyword strings

Respond with ONLY the JSON array. No markdown. No explanation. No wrapper text.
If nothing is worth remembering, respond with exactly: []

Examples of things worth extracting:
- User decided something concrete ("I'm going to drop Project X")
- User committed to a behaviour ("I'll stop checking WhatsApp until noon")
- A pattern emerged ("User keeps avoiding the investor call")
- A win or miss ("Closed first customer today")

Examples of things NOT worth extracting:
- "Hi, how are you" type exchanges
- Questions about general knowledge
- Anything the AI said generically without the user confirming it
"""


class MemoryExtractor:
    """
    Extracts structured memories from conversation exchanges.

    Designed to run as a FastAPI BackgroundTask — never blocks the chat response.
    Failures are silent from the user's perspective (logged server-side).
    """

    def __init__(self, orchestrator):
        # Accepts AIOrchestrator — injected to avoid circular imports at module level
        self.orchestrator = orchestrator

    async def extract_and_save(
        self,
        user_id: UUID,
        user_message: str,
        assistant_response: str,
        memory_repo,
    ) -> list[Memory]:
        """
        Extract memories from one exchange and persist them.
        Entry point for BackgroundTask usage.
        """
        try:
            memories = await self._extract(user_message, assistant_response)
            saved = 0
            for memory in memories:
                await memory_repo.save(user_id, memory)
                saved += 1
            if saved:
                log.info("memories_extracted", user_id=str(user_id), count=saved)
            return memories
        except Exception as e:
            # Never crash the chat over a memory extraction failure
            log.warning("memory_extraction_failed", error=str(e), user_id=str(user_id))
            return []

    async def _extract(self, user_message: str, assistant_response: str) -> list[Memory]:
        """
        Call the AI with extraction prompt, parse structured response.
        Trims inputs to control token usage.
        """
        prompt = EXTRACTION_PROMPT.format(
            user_message=user_message[:400],
            assistant_response=assistant_response[:600],
        )

        # inject_full_context=False: this is a utility call, not a conversation.
        # No need to load the full user profile for extraction.
        response = await self.orchestrator.think(
            prompt,
            inject_full_context=False,
        )

        return self._parse_response(response.content)

    def _parse_response(self, raw: str) -> list[Memory]:
        """Parse the JSON array response into Memory objects."""
        raw = raw.strip()

        # Strip markdown fences if model added them despite instructions
        if "```" in raw:
            parts = raw.split("```")
            for part in parts:
                part = part.strip()
                if part.startswith("json"):
                    part = part[4:].strip()
                if part.startswith("["):
                    raw = part
                    break

        # Find the JSON array bounds in case there's extra text
        start = raw.find("[")
        end = raw.rfind("]")
        if start == -1 or end == -1:
            return []
        raw = raw[start:end + 1]

        try:
            items = json.loads(raw)
        except json.JSONDecodeError as e:
            log.debug("memory_json_parse_error", error=str(e), raw_preview=raw[:80])
            return []

        if not isinstance(items, list):
            return []

        memories = []
        for item in items:
            if not isinstance(item, dict):
                continue
            try:
                raw_type = item.get("type", "insight").lower().strip()
                # Gracefully handle slight variations the model might return
                type_map = {
                    "decision": MemoryType.DECISION,
                    "commitment": MemoryType.COMMITMENT,
                    "insight": MemoryType.INSIGHT,
                    "conversation": MemoryType.CONVERSATION,
                    "pattern": MemoryType.PATTERN,
                    "win": MemoryType.WIN,
                    "miss": MemoryType.MISS,
                }
                mem_type = type_map.get(raw_type, MemoryType.INSIGHT)
                content = item.get("content", "").strip()
                if not content:
                    continue
                memory = Memory(
                    type=mem_type,
                    content=content,
                    context=item.get("context", "").strip(),
                    tags=item.get("tags", []),
                )
                memories.append(memory)
            except Exception as e:
                log.debug("memory_item_parse_error", error=str(e))
                continue

        return memories
