# CorpusAI API Contract v2

The complete backend contract for the frontend. Every endpoint, every field, every error.
This is the input document for the CorpusAI-FE.

**Breaking vs v1.0.0:** everything now requires auth; chat moved to server-minted sessions
(`/api/chats/{sessionId}/messages` replaces `/api/chat/{subjectId}/message`); the old
`/api/quiz` is now `/api/flashcards`, and `/api/quizzes` is a new multiple-choice feature.

- Base URL: `http://localhost:8080`
- Types below are TypeScript. `| null` means the field **can be null in a successful response** —
  those are the ones worth guarding.

---

## Conventions

### Auth

Every endpoint except `POST /api/auth/register`, `POST /api/auth/login` and
`GET /actuator/health` requires:

```
Authorization: Bearer <token>
```

A missing, malformed, expired, or wrongly-signed token is a `401` — the filter never
distinguishes them, so treat any 401 as "log in again".

### Requests

JSON bodies need `Content-Type: application/json` (else `415`). The only exception is
document upload, which is `multipart/form-data`.

### Errors

**Every** failure returns this shape — there is no other error body:

```ts
interface ErrorResponse {
  error: string;    // stable machine-readable code, safe to branch on
  message: string;  // human-readable, safe to display, not stable
}
```

| Status | `error` | Typical cause |
|---|---|---|
| 400 | `BAD_REQUEST` | Malformed body, bad path-variable type, missing query param |
| 400 | `VALIDATION_ERROR` | A `@Valid` constraint failed; `message` names the field |
| 401 | `UNAUTHORIZED` | Missing/invalid/expired token, or bad login |
| 403 | `FORBIDDEN` | Authenticated but not allowed (no subject access, not admin, not owner) |
| 404 | `NOT_FOUND` | Unknown subject/session/set/quiz/document/user, or unknown route |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP verb |
| 409 | `CONFLICT` | Duplicate email, duplicate subject name, quiz already submitted, generation on a subject with no content |
| 413 | `PAYLOAD_TOO_LARGE` | Upload over 50MB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Missing/wrong `Content-Type` |
| 500 | `INTERNAL_ERROR` / `SERVER_ERROR` | Server-side or upstream LLM failure |

> **403 vs 404 on someone else's resource.** Asking for a chat/set/quiz that exists but
> belongs to another user returns **403**, not 404 — the id is confirmed to exist. A
> genuinely unknown id returns 404.

### Enums

```ts
type ModelProvider  = "OPENAI" | "ANTHROPIC";
type Role           = "USER" | "ADMIN";
type Difficulty     = "EASY" | "MEDIUM" | "HARD";
type DocumentStatus = "PENDING" | "INGESTING" | "READY" | "FAILED";
type MessageRole    = "USER" | "ASSISTANT";
type Lang           = "en" | "sr";
```

### Types

- `UUID` — string, e.g. `"3f2a1c8e-..."`.
- `Instant` — ISO-8601 UTC, e.g. `"2026-07-16T12:29:46.123Z"`.

### Generation defaults

`POST /api/flashcards/{subjectId}/generate` and `POST /api/quizzes/{subjectId}/generate`
share these. **Every field is optional** — `{}` is a valid body:

| Field | Default | Bounds |
|---|---|---|
| `topic` | `null` (whole subject) | ≤ 200 chars. Whitespace-only is normalised to `null` |
| `count` | `5` | 1–20 |
| `lang` | **`"sr"`** | `"en"` \| `"sr"` |
| `provider` | `"OPENAI"` | `"OPENAI"` \| `"ANTHROPIC"` |

> `lang` defaults to **Serbian**, not English.

---

## Auth — `/api/auth`

### `POST /api/auth/register` — public → `200`

```ts
interface RegisterRequest {
  email: string;        // must be a valid email
  password: string;     // min 8 characters
  displayName: string;  // non-blank
}

interface AuthResponse {
  token: string;
  user: UserResponse;
}

interface UserResponse {
  id: UUID;
  email: string;
  displayName: string;
  role: Role;           // always "USER" — registration cannot create an admin
}
```

Errors: `400 VALIDATION_ERROR`, `409 CONFLICT` (email taken).

> A new account has **no subject access**. `GET /api/subjects` returns `[]` until an admin
> grants one. The FE should expect and handle the empty state, not treat it as an error.

### `POST /api/auth/login` — public → `200`

```ts
interface LoginRequest { email: string; password: string; }
// → AuthResponse
```

Errors: `400 VALIDATION_ERROR`, `401 UNAUTHORIZED` (wrong email or password — deliberately
not distinguished).

### `GET /api/auth/me` — user → `200`

Returns `UserResponse`. Errors: `401`.

---

## Subjects — `/api/subjects`

### `GET /api/subjects` — user → `200`

```ts
interface SubjectResponse {
  id: string;             // slug, e.g. "softverski-proces" — stable, used as a path param
  displayName: string;
  displayNameSr: string;
}
// → SubjectResponse[]
```

Only subjects the caller has been granted. Admins see all non-archived subjects. Archived
subjects never appear. Errors: `401`.

---

## Chat — `/api/chats`

Sessions are **server-minted**. `lang` and `provider` are fixed at creation and cannot be
changed — to switch either, create a new session.

### `POST /api/chats` — user → `201`

```ts
interface CreateChatSessionRequest {
  subjectId: string;      // required
  lang: Lang;             // required
  provider: ModelProvider;// required
}

interface ChatSessionResponse {
  id: UUID;
  title: string | null;   // null until the first message is sent
  subjectId: string;
  lang: Lang;
  provider: ModelProvider;
  createdAt: Instant;
}
```

> **`title` is `null` on creation.** It's derived from the first user message (truncated to
> ~60 chars). Render a placeholder for new sessions.

Errors: `400 VALIDATION_ERROR`, `403` (no access to subject), `404` (unknown subject).

### `GET /api/chats?subjectId={slug}` — user → `200`

Returns `ChatSessionResponse[]`, newest first, caller's own sessions only.
`subjectId` is **required** (omitting it is `400 BAD_REQUEST`).

Errors: `400`, `401`, `404` (unknown subject).

### `GET /api/chats/{sessionId}/messages` — user → `200`

```ts
interface ChatMessageResponse {
  id: UUID;
  role: MessageRole;
  content: string;
  createdAt: Instant;
}
// → ChatMessageResponse[]  (chronological)
```

Errors: `400` (non-UUID id), `403` (not owner), `404` (unknown session).

### `DELETE /api/chats/{sessionId}` — user → `204`

Deletes the session and its messages. Errors: `400`, `403`, `404`.

### `POST /api/chats/{sessionId}/messages` — user → **SSE**

See [Streaming contract](#streaming-contract-sse) below.

---

## Streaming contract (SSE)

`POST /api/chats/{sessionId}/messages` streams the answer. It is a **POST with a body**, so
`EventSource` cannot be used — use `fetch-event-source` (or equivalent) so the
`Authorization` header can be sent.

**Request**

```ts
interface SendMessageRequest { message: string; }  // non-blank
```

**Stream** — two named event types, in order:

```
event:token
data:{"token":"Observer"}

event:token
data:{"token":" pattern"}

... N token events ...

event:done
data:{"messageId":"e0d88012-...","inputTokens":219,"outputTokens":20,"latencyMs":1275}
```

```ts
interface ChatChunkResponse { token: string; }         // event: "token"

interface ChatDoneResponse {                            // event: "done"
  messageId: UUID;
  inputTokens: number | null;
  outputTokens: number | null;
  latencyMs: number;
}
```

Notes for the FE:

- Concatenate `token` values in arrival order to rebuild the answer.
- **`inputTokens`/`outputTokens` can be `null`** when the provider reports no usage. Don't
  assume a number.
- `done` fires once, immediately before the stream closes. It is the completion signal.
- The **assistant message is persisted when the stream completes**, not per token. A client
  that disconnects early may leave the transcript without the assistant reply.
- Errors *before* streaming starts (403/404) arrive as a normal JSON `ErrorResponse` with the
  matching status. Errors *after* streaming has begun terminate the stream — no `done` event
  and no error body. **Treat "stream ended without `done`" as a failure.**
- Timeout is 300s.

---

## Flashcards — `/api/flashcards`

### `POST /api/flashcards/{subjectId}/generate` — user → `200`

Body: the shared [generation request](#generation-defaults); all fields optional.

```ts
interface FlashcardSetResponse {
  setId: UUID;
  subjectId: string;
  topic: string | null;
  lang: Lang;
  provider: ModelProvider;
  createdAt: Instant;
  cards: FlashcardResponse[];
}

interface FlashcardResponse {
  question: string;
  answer: string;
  difficulty: Difficulty;   // literal EASY/MEDIUM/HARD in both languages
  sourceHint: string | null;
}
```

> `difficulty` is **not translated** — it is `EASY`/`MEDIUM`/`HARD` even when `lang: "sr"`.
> Translate it in the FE.

This call takes seconds (a real LLM request) — show a loading state.

Errors: `400 VALIDATION_ERROR` (count/topic/lang), `400 BAD_REQUEST` (unknown provider),
`403`, `404`, `409 CONFLICT` (the subject has no ingested documents yet — upload some first),
`500` (LLM failure or an unusable model response).

### `GET /api/flashcards?subjectId={slug}` — user → `200`

```ts
interface FlashcardSetSummaryResponse {
  setId: UUID;
  subjectId: string;
  topic: string | null;
  lang: Lang;
  provider: ModelProvider;
  createdAt: Instant;
}
// → FlashcardSetSummaryResponse[]  (newest first, own sets only; no cards)
```

`subjectId` required. Errors: `400`, `401`, `404`.

### `GET /api/flashcards/{setId}` — user → `200`

Returns `FlashcardSetResponse` (cards in stable order). Errors: `400`, `403`, `404`.

### `DELETE /api/flashcards/{setId}` — user → `204`

Errors: `400`, `403`, `404`.

---

## Quizzes — `/api/quizzes`

**Grading is server-side.** `correctIndex` and `explanation` are never sent on generation —
only after a submit. Don't build a client-side scoring path; it cannot work.

### `POST /api/quizzes/{subjectId}/generate` — user → `200`

Body: the shared [generation request](#generation-defaults).

```ts
interface QuizResponse {
  quizId: UUID;
  subjectId: string;
  topic: string | null;
  lang: Lang;
  provider: ModelProvider;
  createdAt: Instant;
  questions: QuizQuestionResponse[];
}

interface QuizQuestionResponse {
  id: UUID;
  question: string;
  options: string[];   // always exactly 4
}
```

Errors: as flashcards.

### `POST /api/quizzes/{quizId}/submit` — user → `200`

```ts
interface QuizSubmissionRequest {
  answers: {
    questionId: UUID;
    selectedIndex: number;   // 0-3
  }[];                       // must not be empty
}

interface QuizSubmissionResponse {
  score: number;
  total: number;
  results: {
    questionId: UUID;
    correct: boolean;
    correctIndex: number;
    explanation: string | null;
  }[];
}
```

Rules:

- **Partial submits are allowed** — an unanswered question counts as wrong. `results` always
  covers every question, not just answered ones.
- **One submit per quiz.** A second attempt is `409 CONFLICT`.
- Duplicate `questionId`s, or ids from another quiz, are `400 BAD_REQUEST`.

Errors: `400 VALIDATION_ERROR` (empty answers, `selectedIndex` outside 0–3), `400 BAD_REQUEST`,
`403`, `404`, `409 CONFLICT`.

### `GET /api/quizzes/{quizId}` — user → `200`

**The response has two states.** Branch on `completedAt`:

```ts
interface QuizDetailResponse {
  quizId: UUID;
  subjectId: string;
  topic: string | null;
  lang: Lang;
  provider: ModelProvider;
  questionCount: number;
  score: number | null;         // null until submitted
  completedAt: Instant | null;  // null until submitted — use as the discriminator
  createdAt: Instant;
  questions: QuestionDetail[];
}

interface QuestionDetail {
  id: UUID;
  question: string;
  options: string[];        // always present, exactly 4
  // The four below are OMITTED FROM THE JSON ENTIRELY while completedAt === null
  // (QuestionDetail is annotated @JsonInclude(NON_NULL)) — they are `undefined`, not null:
  selectedIndex?: number;
  correct?: boolean;
  correctIndex?: number;
  explanation?: string;     // may still be absent after submit if the model gave none
}
```

> **These four keys are absent, not null.** Unlike the outer object — where `score` and
> `completedAt` *are* present as `null` — an unanswered question omits the grading keys
> altogether. So `question.correctIndex !== null` is **true** before submitting (because it's
> `undefined`) and will mislead you. Branch on `completedAt`, or use `!= null` / `?.`.

So: `completedAt === null` → a resumable quiz, no answers revealed.
`completedAt !== null` → a rezultati view, grading fields populated.

Errors: `400`, `403`, `404`.

### `GET /api/quizzes?subjectId={slug}` — user → `200`

```ts
interface QuizSummaryResponse {
  quizId: UUID;
  subjectId: string;
  topic: string | null;
  lang: Lang;
  provider: ModelProvider;
  questionCount: number;
  score: number | null;         // null if not yet submitted
  completedAt: Instant | null;
  createdAt: Instant;
}
// → QuizSummaryResponse[]  (newest first, own quizzes only)
```

`subjectId` required. Errors: `400`, `401`, `404`.

### `DELETE /api/quizzes/{quizId}` — user → `204`

Errors: `400`, `403`, `404`.

---

## Admin — `/api/admin`

All of `/api/admin/**` requires `role: "ADMIN"`. A `USER` token gets `403 FORBIDDEN`.

### `GET /api/admin/users` → `200`

```ts
interface AdminUserResponse {
  id: UUID;
  email: string;
  displayName: string;
  role: Role;
  subjectIds: string[];   // granted subject slugs; [] for a new user
}
// → AdminUserResponse[]
```

### `POST /api/admin/users/{userId}/subjects/{subjectId}` → `204`

Grants access. **Idempotent** — granting twice is still `204` and creates one grant.
No request body. Errors: `400` (non-UUID `userId`), `403`, `404` (unknown user or subject).

### `DELETE /api/admin/users/{userId}/subjects/{subjectId}` → `204`

Revokes access. **Idempotent** — revoking a grant that doesn't exist is still `204`.
Errors: `400`, `403`.

### `POST /api/admin/subjects` → `201`

```ts
interface CreateSubjectRequest {
  displayName: string;         // non-blank, ≤255
  displayNameSr: string;       // non-blank, ≤255
  systemPrompt?: string | null;// ≤10000; null/omitted → default template
}

interface AdminSubjectResponse {
  id: string;                  // slug generated from displayName; immutable
  displayName: string;
  displayNameSr: string;
  systemPrompt: string | null; // null means "using the default template"
  archived: boolean;
  createdAt: Instant;
}
```

The slug is derived from `displayName` (lowercased, diacritics stripped, dashes). Creating a
subject whose slug already exists is `409 CONFLICT`.

Errors: `400 VALIDATION_ERROR`, `403`, `409 CONFLICT`.

### `PUT /api/admin/subjects/{subjectId}` → `200`

Body `UpdateSubjectRequest` — same shape as create. Updates names and prompt.
**The slug never changes**, even if `displayName` does.

Errors: `400`, `403`, `404`.

### `DELETE /api/admin/subjects/{subjectId}` → `204`

**Archives** — it is not a hard delete. The subject disappears from `GET /api/subjects`,
all user access is revoked, but documents and embeddings are kept.

Errors: `400`, `403`, `404`.

### `POST /api/admin/subjects/{subjectId}/documents` → `202`

`multipart/form-data`, part name **`file`**. Accepts `.pdf`, `.md`, `.txt`. Max 50MB.

Returns `DocumentResponse` immediately with `status: "PENDING"` — **ingestion is
asynchronous**. Poll the list endpoint until `READY`.

```ts
interface DocumentResponse {
  id: UUID;
  fileName: string;
  status: DocumentStatus;
  uploadedAt: Instant;
  errorMessage: string | null;  // populated only when status === "FAILED"
}
```

> **Re-uploading the same file name replaces the existing document.** It is not a conflict:
> the file on disk is overwritten, the existing row is reset to `PENDING` and re-ingested, and
> the **same** document `id` comes back. There is no separate "replace" endpoint — upload is
> an upsert keyed on `(subjectId, fileName)`. Warn the user before overwriting if that matters.

Errors: `400 BAD_REQUEST` (missing `file` part, unsupported type), `403`, `404` (unknown
subject), `413 PAYLOAD_TOO_LARGE`, `500` (file could not be written to storage).

### `GET /api/admin/subjects/{subjectId}/documents` → `200`

Returns `DocumentResponse[]`. This is the polling endpoint:
`PENDING → INGESTING → READY`, or `FAILED` with `errorMessage` set.

Errors: `403`, `404`.

### `DELETE /api/admin/documents/{documentId}` → `204`

Deletes the row, the file, and its embeddings. Errors: `400`, `403`, `404`.

### `GET /api/admin/metrics` → `200`

Query params, all optional:

| Param | Format | Meaning |
|---|---|---|
| `from` | ISO-8601 instant | Inclusive lower bound |
| `to` | ISO-8601 instant | **Exclusive** upper bound |
| `groupBy` | `provider` \| `model` \| `feature` | Case-insensitive; omitted → totals only |

```ts
interface MetricsResponse {
  overall: UsageTotals;
  groups: UsageGroup[];        // [] when groupBy is omitted
}

interface UsageTotals {
  calls: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
}

interface UsageGroup extends UsageTotals {
  key: string;                 // e.g. "OPENAI" | "gpt-5.6-terra" | "CHAT"
}
```

An empty range returns zeros, never `null`. Note `to` is **half-open**, so adjacent windows
never double-count.

Errors: `400 BAD_REQUEST` (unparseable `from`/`to`, or invalid `groupBy`), `403`.

### `GET /api/admin/metrics/export` → `200`

Same params and aggregation as above, returned as CSV.

```
Content-Type: text/csv
Content-Disposition: attachment; filename="llm-usage-metrics.csv"
```

Columns: `group,calls,totalInputTokens,totalOutputTokens,totalTokens,avgLatencyMs,p95LatencyMs`

The first data row is always `OVERALL`; grouped rows follow when `groupBy` is set.

Errors: `400`, `403`.

---

## Health

### `GET /actuator/health` — public → `200`

```json
{ "status": "UP" }
```

The only non-`/api/auth` endpoint that needs no token.