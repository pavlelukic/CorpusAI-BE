UPDATE subjects
SET id = 'softverski-proces'
WHERE id = 'softverski-procesi';

-- Drop any pre-existing ingestion/embedding data tagged under the old name
DELETE FROM ingestion_log WHERE subject_id = 'softverski-procesi';
DELETE FROM embeddings WHERE metadata->>'subject_id' = 'softverski-procesi';