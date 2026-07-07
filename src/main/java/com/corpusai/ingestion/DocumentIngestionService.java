package com.corpusai.ingestion;

import com.corpusai.config.SubjectsProperties;
import com.corpusai.subject.Subject;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class DocumentIngestionService implements ApplicationRunner {

    private final SubjectsProperties subjectsProperties;
    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final IngestionHashTracker hashTracker;

    public DocumentIngestionService(SubjectsProperties subjectsProperties,
                                    EmbeddingModel embeddingModel,
                                    PgVectorEmbeddingStore embeddingStore,
                                    IngestionHashTracker hashTracker) {
        this.subjectsProperties = subjectsProperties;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.hashTracker = hashTracker;
    }

    @Override
    public void run(ApplicationArguments args) {
        var ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 30))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        for (Subject subject : subjectsProperties.subjects()) {
            log.info("Checking ingestion for subject: {}", subject.id());
            List<Document> docs = loadDocuments(subject.documentsPath());

            int ingested = 0;
            Set<String> onDisk = new HashSet<>();
            for (Document doc : docs) {
                String sourceFile = doc.metadata().getString(Document.FILE_NAME);
                if (sourceFile == null) sourceFile = "unknown";
                onDisk.add(sourceFile);

                String contentHash = IngestionHashTracker.sha256(doc.text());

                if(hashTracker.alreadyIngested(subject.id(), sourceFile, contentHash)) {
                    log.debug("Skipping unchanged: {}/{}", subject.id(), sourceFile);
                    continue;
                }

                doc.metadata().put("subject_id", subject.id());
                ingestor.ingest(doc);
                hashTracker.recordIngestion(subject.id(), sourceFile, contentHash);
                ingested++;
                log.info("Ingested: {}/{}", subject.id(), sourceFile);
            }

            int removed = removeStaleDocuments(subject.id(), onDisk);

            log.info("Ingestion complete for {}: {} new documents, {} stale removed.",
                    subject.id(), ingested, removed);
        }
    }

    private int removeStaleDocuments(String subjectId, Set<String> onDisk) {
        int removed = 0;
        for (String sourceFile : hashTracker.sourceFilesFor(subjectId)) {
            if (onDisk.contains(sourceFile)) continue;

            embeddingStore.removeAll(new And(
                    new IsEqualTo("subject_id", subjectId),
                    new IsEqualTo("file_name", sourceFile)));
            hashTracker.deleteIngestionRecord(subjectId, sourceFile);
            removed++;
            log.info("Removed stale ingestion for {}/{}", subjectId, sourceFile);
        }
        return removed;
    }

    private List<Document> loadDocuments(String documentsPath) {
        var pdfMatcher = FileSystems.getDefault().getPathMatcher("glob:**.pdf");
        var textMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{md,txt}");
        var pdfParser = new ApachePdfBoxDocumentParser();

        List<Document> all = new ArrayList<>();
        all.addAll(ClassPathDocumentLoader.loadDocumentsRecursively(documentsPath, pdfMatcher, pdfParser));
        all.addAll(ClassPathDocumentLoader.loadDocumentsRecursively(documentsPath, textMatcher));
        return all;
    }


}
