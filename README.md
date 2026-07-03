# CorpusAI Backend

RAG-based subject-specific AI tutor built with Spring Boot 3.5 and LangChain4j.
Developed as part of a master thesis: *Analysis and Implementation of the LangChain4j
Library in a Java Programming Environment*.

## Tech Stack

- Java 21 / Spring Boot 3.5 / Maven
- LangChain4j 1.10.0 — RAG pipeline, AI services, streaming
- PostgreSQL + pgvector — vector similarity search
- OpenAI — embeddings (`text-embedding-3-small`) and chat (`gpt-4o-mini`)
- Anthropic — alternative chat provider (Claude)

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose
- OpenAI API key
- Anthropic API key

## Setup

**1. Clone and configure environment**

```bash
cp .env.example .env
```

Fill in `.env`:
```
OPENAI_API_KEY=sk-...
ANTHROPIC_API_KEY=sk-ant-...
DB_PASSWORD=your_password
```

**2. Start the database**

```bash
docker compose up -d
```

This starts a PostgreSQL 16 container with the pgvector extension enabled.

**3. Run the application**

```bash
./mvnw spring-boot:run
```

On first startup, the ingestion pipeline embeds all documents found under
`src/main/resources/documents/` and stores them in pgvector. Subsequent startups
skip already-ingested files via SHA-256 hash tracking.

## Adding Documents

Drop PDF, Markdown, or `.txt` files into the subject's documents folder:

```
src/main/resources/documents/softverski-procesi/
src/main/resources/documents/softverski-paterni/
src/main/resources/documents/projektovanje-softvera/
```

Restart the app — new files are detected and ingested automatically.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/subjects` | List all available subjects |
| `POST` | `/api/chat/{subjectId}/message` | Stream a chat response (SSE) |
| `POST` | `/api/quiz/{subjectId}/generate` | Generate flashcards |

### Chat request
```json
{
  "sessionId": "unique-session-id",
  "message": "Šta je to vodopad (waterfall) model?",
  "lang": "sr"
}
```

### Quiz request
```json
{
  "topic": "java design patterns",
  "count": 5,
  "lang": "en"
}
```

Both `topic` and `count` are optional (defaults: no topic filter, 5 flashcards).

## Project Structure

```
src/main/java/com/corpusai/
├── chat/          # SSE streaming chat, memory, RAG augmentor
├── config/        # CORS, error handling, subjects config
├── ingestion/     # Document loading, chunking, embedding, hash tracking
├── model/         # ModelFactory — OpenAI and Anthropic model builders
├── quiz/          # Flashcard generation with structured output
└── subject/       # Subjects listing endpoint
```
