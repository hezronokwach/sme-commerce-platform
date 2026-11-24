# 0020: Gemini through Spring AI's Google GenAI client, and Qwen on Groq

Status: Accepted (2026-11-24). Changes how ADR 0015 calls Gemini and which Groq model ADR 0016 uses.

## Context

The first run of the assistant evaluation with real keys (issue "Assistant UI + evaluation") failed
in two places that the tests could not see, because they use fake models:

1. **Embeddings.** ADR 0015 called Gemini through its OpenAI-compatible endpoint with Spring AI's OpenAI
   client. Gemini's answer there has no `usage` field (and no `index` per vector). The OpenAI Java SDK
   behind Spring AI treats `usage` as required, so every embedding call failed with "`usage` is not set"
   and every document ended FAILED.
2. **Chat.** Groq has retired `llama-3.3-70b-versatile`, so every question got a 404 and the customer saw
   "The assistant can't answer right now".

## Decision

1. **Embeddings use `spring-ai-starter-model-google-genai-embedding`**, Spring AI's own client for
   Gemini, with the same model (`gemini-embedding-001`) and the same 768 dimensions. The stored vectors
   and the V80 table don't change. `spring.ai.model.embedding=google-genai` switches the OpenAI embedding
   client off and `spring.ai.model.embedding.text=google-genai` switches Google's on. Chat stays on the
   OpenAI-compatible client pointed at Groq.
2. **Chat uses `qwen/qwen3.8-27b` on Groq** (still overridable with `GROQ_MODEL`). We compared Groq's
   current models on the 15-question evaluation:
   - `openai/gpt-oss-120b` scored 14 of 15. It twice wrote "M-Pesa" as "M-Paysa", with a hidden
     zero-width space, which a Kenyan shop can't have. It also spends tokens reasoning before it answers.
   - `qwen/qwen3.8-27b` scored 15 of 15, spelled M-Pesa right, and answers without reasoning tokens.
3. **The evaluation scores meaning, not typing.** The checker ignores spaces and typographic apostrophes
   and dashes ("9 am" matches "9am", "don’t" matches "don't"), and the "can't answer right now" reply is
   always a fail. One case forbade "50" to catch another shop's price leaking in, but that also matched
   the shop's own "KES 750"; it now forbids "KES 50".

## Consequences

- One more dependency (Google's GenAI SDK, through the starter), in exchange for a client made for
  Gemini instead of a compatibility layer that breaks on a missing field.
- Fake models in tests can't catch provider changes like these. The evaluation with real keys is the
  check, so it should be run again after changing a model or provider.
- The score is in `docs/assistant-evaluation.md`.
