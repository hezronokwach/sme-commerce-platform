# 0007: Groq is the LLM provider for the assistant

Status: Accepted (2026-11-05)

## Context

The assistant module (issues "Assistant part 1–3") answers customer questions from a shop's own
products and documents (retrieval-augmented generation). The architecture doc leaves the provider open
("LLM provider"), asking only that the model sit behind Spring AI's interfaces so it can be swapped.
The owner chose Groq over OpenAI.

## Decision

- **Chat model: Groq.** Groq's API is OpenAI-compatible, so Spring AI's OpenAI chat client is used with
  Groq's base URL (`https://api.groq.com/openai`) and a Groq API key. The model name is a setting, not code.
- **Secrets:** the key lives only in the environment (`GROQ_API_KEY`), like `JWT_SECRET`: in the
  git-ignored `backend/.env` locally and in Render's environment in production.
- **The code depends on Spring AI's `ChatClient` / `ChatModel`, never on Groq types**, so changing the
  provider stays a configuration change.

## Open: embeddings

Groq offers chat models but no embeddings endpoint, and retrieval needs embeddings to fill the pgvector
store. This is decided in the assistant ingestion issue. The candidates:

- a local embedding model inside the app (Spring AI's ONNX transformers module): no extra key or cost,
  but more memory on the Render instance;
- a separate hosted embeddings provider, with its own key.

Whichever is chosen, the vector size of the `vector_store` table follows from it.

## Consequences

- No OpenAI account or key is needed.
- Groq's rate limits and model list differ from OpenAI's; the assistant's rate limit and token budget
  (architecture doc, security table) must be set with Groq's limits in mind.
