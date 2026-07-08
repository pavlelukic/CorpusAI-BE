package com.corpusai.ingestion;

import com.corpusai.subject.Subject;
import com.corpusai.subject.SubjectService;
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

    private static final String DOCUMENTS_CLASSPATH_PREFIX = "documents/";

    private final SubjectService subjectService;
    private final EmbeddingModel embeddingModel;
    private final PgVectorEmbeddingStore embeddingStore;
    private final IngestionHashTracker hashTracker;

    public DocumentIngestionService(SubjectService subjectService,
                                    EmbeddingModel embeddingModel,
                                    PgVectorEmbeddingStore embeddingStore,
                                    IngestionHashTracker hashTracker) {
        this.subjectService = subjectService;
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

        for (Subject subject : subjectService.listActive()) {
            log.info("Checking ingestion for subject: {}", subject.getId());
            List<Document> docs = loadDocuments(DOCUMENTS_CLASSPATH_PREFIX + subject.getId());

            int ingested = 0;
            Set<String> onDisk = new HashSet<>();
            for (Document doc : docs) {
                String sourceFile = doc.metadata().getString(Document.FILE_NAME);
                if (sourceFile == null) sourceFile = "unknown";
                onDisk.add(sourceFile);

                String contentHash = IngestionHashTracker.sha256(doc.text());

                if(hashTracker.alreadyIngested(subject.getId(), sourceFile, contentHash)) {
                    log.debug("Skipping unchanged: {}/{}", subject.getId(), sourceFile);
                    continue;
                }

                doc.metadata().put("subject_id", subject.getId());
                ingestor.ingest(doc);
                hashTracker.recordIngestion(subject.getId(), sourceFile, contentHash);
                ingested++;
                log.info("Ingested: {}/{}", subject.getId(), sourceFile);
            }

            int removed = removeStaleDocuments(subject.getId(), onDisk);

            log.info("Ingestion complete for {}: {} new documents, {} stale removed.",
                    subject.getId(), ingested, removed);
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
