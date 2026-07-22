-- Ties a CHAT usage row to the assistant message it paid for, so the transcript endpoint can show
-- per-message stats after a reload instead of only for replies streamed in the current page session.
-- Null for every other feature: flashcards, quizzes and query compression produce no chat message.
--
-- Deliberately NOT a foreign key to chat_messages, matching session_id in V9. Deleting a chat session
-- cascades its messages away, and these rows are the thesis measurements - they must outlive it. The
-- column is a soft reference: a null-or-dangling id degrades to "no stats", never to lost usage data.
ALTER TABLE llm_usage ADD COLUMN message_id UUID NULL;

-- The transcript lookup is "usage rows for this session that belong to a message", so the partial
-- index matches that predicate exactly and stays small - only CHAT rows carry a message_id at all.
CREATE INDEX idx_llm_usage_session_message ON llm_usage(session_id) WHERE message_id IS NOT NULL;
