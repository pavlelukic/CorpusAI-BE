# CorpusAI Backend

RAG-based subject-specific AI tutor built with Spring Boot 3.5 and LangChain4j.
Developed as part of a master thesis: *Analysis and Implementation of the LangChain4j
Library in a Java Programming Environment*.

Students chat with a tutor grounded in their course materials, and generate flashcards
and multiple-choice quizzes from the same documents. Every LLM call is measured
(tokens + latency) to provide the data for a comparison within the thesis.

## Tech Stack

- Java 21 / Spring Boot 3.5.14 / Maven
- LangChain4j 1.10.0 — RAG pipeline, AI services, streaming, structured output
- PostgreSQL 16 + pgvector — vector similarity search (`embeddings` table managed by LangChain4j)
- Flyway — schema migrations (`V1`–`V9`), applied automatically on boot
- Spring Security + jjwt — stateless JWT auth, BCrypt passwords
- OpenAI + Anthropic — both selectable per chat session / generation

### Models

The provider is chosen by the user per request; the concrete model is fixed in code.
Two roles, because chat streams token-by-token and favours a fast model, while
flashcard/quiz generation needs stronger structured-output behaviour.

| Role | OpenAI | Anthropic |
|---|---|---|
| Chat + query compression | `gpt-5.4-mini` | `claude-haiku-4-5` |
| Generation (flashcards, quizzes) | `gpt-5.6-terra` | `claude-sonnet-5` |
| Embeddings | `text-embedding-3-small` (1536 dims) | — |

Source of truth: `ModelFactory.chatModelName()` / `generationModelName()`. Note that the
frontier generation models only accept their default temperature, so `ModelFactory` sends
an explicit temperature only for the models that support one.

## Prerequisites

- Java 21+
- Maven 3.9+ (or the bundled `./mvnw`)
- Docker + Docker Compose
- An OpenAI API key and an Anthropic API key

## Setup

**1. Configure environment**

```bash
cp .env.example .env
```

Fill in `.env` — every variable is required unless it has a default:

| Variable | Purpose |
|---|---|
| `OPENAI_API_KEY` | OpenAI chat + embeddings |
| `ANTHROPIC_API_KEY` | Anthropic chat |
| `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Postgres; used by both docker-compose and the app |
| `JWT_SECRET` | HMAC-SHA256 signing secret, **min 32 bytes** — `openssl rand -base64 48` |
| `JWT_EXPIRATION_HOURS` | Token lifetime (default `24`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Seed admin, created on first boot if missing |
| `STORAGE_ROOT` | Document root (default `./storage/documents`) |

**2. Start the database**

```bash
docker compose up -d
```

PostgreSQL 16 with pgvector enabled, on port 5432.

**3. Run the application**

```bash
./mvnw spring-boot:run
```

On boot the app: applies Flyway migrations, seeds the three subjects and the admin user,
and runs the ingestion reconciler (walks `STORAGE_ROOT` against the `documents` table,
ingesting anything new or changed and removing embeddings for files that disappeared).

Health check: `http://localhost:8080/actuator/health`.

## Authentication & Roles

Everything except `/api/auth/**` and `/actuator/health` requires a JWT:

```
Authorization: Bearer <token>
```

Obtain one from `POST /api/auth/register` or `POST /api/auth/login`.

| Role | Can do |
|---|---|
| `USER` | Chat, flashcards, quizzes — **only for subjects an admin granted them** |
| `ADMIN` | Everything a user can, on all subjects, plus `/api/admin/**` |

**New accounts start with no subject access.** A fresh user sees an empty `GET /api/subjects`
until an admin grants them a subject. Self-registration always creates a `USER`; the only
`ADMIN` is the one seeded from `.env`.

Every failure returns the same JSON shape, so the frontend can parse errors uniformly:

```json
{ "error": "NOT_FOUND", "message": "Unknown subject: no-such-subject" }
```

| Status | `error` |
|---|---|
| 400 | `BAD_REQUEST`, `VALIDATION_ERROR` |
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `CONFLICT` |
| 413 | `PAYLOAD_TOO_LARGE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 500 | `INTERNAL_ERROR`, `SERVER_ERROR` |

## API

Full request/response schemas live in [`docs/api-contract-v2.md`](docs/api-contract-v2.md).
Overview:

### Auth — `/api/auth`
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/register` | public | `{email, password, displayName}` → token + user |
| `POST` | `/login` | public | `{email, password}` → token + user |
| `GET` | `/me` | user | Current user |

### Subjects — `/api/subjects`
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/` | user | Subjects the caller may access (admins: all) |

### Chat — `/api/chats`
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/` | user | Create session `{subjectId, lang, provider}` — lang/provider locked per session |
| `GET` | `/?subjectId=` | user | Own sessions, newest first |
| `POST` | `/{sessionId}/messages` | user | **SSE stream** — `token` events, then a final `done` event |
| `GET` | `/{sessionId}/messages` | user | Full transcript |
| `DELETE` | `/{sessionId}` | user | Delete session + messages |

### Flashcards — `/api/flashcards`
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/{subjectId}/generate` | user | `{topic?, count?, lang?, provider?}` → set + cards |
| `GET` | `/?subjectId=` | user | Own sets, newest first |
| `GET` | `/{setId}` | user | One set with its cards |
| `DELETE` | `/{setId}` | user | Delete set + cards |

### Quizzes — `/api/quizzes`
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/{subjectId}/generate` | user | `{topic?, count?, lang?, provider?}` → quiz, **no correct answers** |
| `POST` | `/{quizId}/submit` | user | `{answers:[{questionId, selectedIndex}]}` → score + results. One submit only |
| `GET` | `/?subjectId=` | user | Own quizzes, newest first |
| `GET` | `/{quizId}` | user | One quiz; grading fields only once completed |
| `DELETE` | `/{quizId}` | user | Delete quiz + questions |

### Admin — `/api/admin`
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/users` | All users with their granted subjects |
| `POST` | `/users/{userId}/subjects/{subjectId}` | Grant access (idempotent) |
| `DELETE` | `/users/{userId}/subjects/{subjectId}` | Revoke access (idempotent) |
| `POST` | `/subjects` | Create subject (slug generated from name) |
| `PUT` | `/subjects/{subjectId}` | Update names / system prompt (slug immutable) |
| `DELETE` | `/subjects/{subjectId}` | Archive — hides it and revokes access; embeddings kept |
| `POST` | `/subjects/{subjectId}/documents` | Upload pdf/md/txt (multipart `file`, max 50MB) → async ingest |
| `GET` | `/subjects/{subjectId}/documents` | Documents with ingestion status |
| `DELETE` | `/documents/{documentId}` | Delete row, file, and its embeddings |
| `GET` | `/metrics?from=&to=&groupBy=` | LLM usage totals; `groupBy` = `provider`\|`model`\|`feature` |
| `GET` | `/metrics/export` | Same aggregation as CSV |

**Quiz grading is server-side.** `correctIndex` never leaves the backend on generation —
only after a submit.

## Managing Content

Documents are **not** read from the classpath. They are uploaded through the admin API and
stored at `{STORAGE_ROOT}/{subjectId}/{fileName}`:

1. `POST /api/admin/subjects` — creates the subject and its storage directory
2. `POST /api/admin/subjects/{id}/documents` — returns immediately with status `PENDING`
3. Ingestion runs asynchronously: `PENDING → INGESTING → READY` (or `FAILED` with a message)
4. Poll `GET /api/admin/subjects/{id}/documents` until `READY`, then chat about it

Seeded subjects: `softverski-proces`, `softverski-paterni`, `projektovanje-softvera`.

## Design Patterns

Made deliberately visible, as the three features share one pipeline rather than duplicating it:

| Pattern | Where |
|---|---|
| Strategy + Factory | `ModelFactory` — selects and caches provider implementations behind LangChain4j's `ChatModel` / `StreamingChatModel` |
| Repository | Spring Data JPA repositories for every aggregate |
| Facade / Composition | `ChatService`, `FlashcardService`, `QuizService` are thin facades over a shared `SubjectContentRetriever`, `ModelFactory` and `UsageRecorder` |
| Template Method (config) | Default prompt template per subject, with an optional per-subject DB override (`SubjectService.systemPromptFor`) |
| Decorator | `RecordingChatModel` wraps a `ChatModel` so hidden query-compression calls are still measured |
| DTO records | Records on the wire; entities never leave the service layer |

## Testing

```bash
./mvnw verify
```

144 tests, all LLM-free — they run without API keys because every path either fails before a
model call or seeds fixtures directly.

- Tests run against the **real dev Postgres** from `docker compose`, with `@Transactional`
  rollback per test — not Testcontainers, not H2. Start the database first.
- `ErrorContractTest` pins the `{error, message}` shape on every failure path.
- **Not covered automatically:** the LLM success paths (`generate()` and the chat SSE
  endpoint). `onCompleteResponse` runs on a LangChain4j worker thread that cannot see
  uncommitted test data and commits independently of the test transaction. These are
  verified manually against both providers.

## Project Structure

```
src/main/java/com/corpusai/
├── auth/          # Users, JWT, Spring Security, subject-access grants, admin user endpoints
├── chat/          # Sessions, DB-backed chat memory, SSE streaming, RAG augmentor
├── config/        # Global error handling, vector store config
├── document/      # Document entity, admin upload/list/delete
├── flashcards/    # Flashcard generation + history
├── ingestion/     # Loading, chunking, embedding, startup reconciler
├── metrics/       # llm_usage recording + admin analytics/CSV export
├── model/         # ModelFactory — OpenAI/Anthropic builders, model roles
├── quiz/          # MCQ generation, server-side grading, history
├── rag/           # SubjectContentRetriever — retrieval shared by flashcards and quizzes
└── subject/       # Subjects, slug generation, prompt resolution, admin CRUD
```