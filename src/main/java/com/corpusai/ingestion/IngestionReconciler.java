package com.corpusai.ingestion;

import com.corpusai.document.Document;
import com.corpusai.document.DocumentRepository;
import com.corpusai.document.DocumentStatus;
import com.corpusai.subject.Subject;
import com.corpusai.subject.SubjectService;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class IngestionReconciler implements ApplicationRunner {

    private final SubjectService subjectService;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final PgVectorEmbeddingStore embeddingStore;
    private final StorageProperties storageProperties;

    public IngestionReconciler(SubjectService subjectService,
                               DocumentRepository documentRepository,
                               IngestionService ingestionService,
                               PgVectorEmbeddingStore embeddingStore,
                               StorageProperties storageProperties) {
        this.subjectService = subjectService;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.embeddingStore = embeddingStore;
        this.storageProperties = storageProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path root = Path.of(storageProperties.root());

        for (Subject subject : subjectService.listActive()) {
            reconcileSubject(subject.getId(), root.resolve(subject.getId()));
        }
    }

    private void reconcileSubject(String subjectId, Path subjectDir) {
        try {
            Files.createDirectories(subjectDir);
        } catch (IOException ex) {
            log.error("Could not create storage directory {}", subjectDir, ex);
            return;
        }

        Map<String, String> onDiskHashes = hashOnDiskFiles(subjectDir);
        List<Document> dbDocuments = documentRepository.findAllBySubjectIdOrderByUploadedAtDesc(subjectId);

        int removed = removeStale(subjectId, dbDocuments, onDiskHashes);
        int ingested = ingestNewOrChanged(subjectId, dbDocuments, onDiskHashes);

        log.info("Reconciliation complete for {}: {} ingested, {} removed.", subjectId, ingested, removed);
    }

    private int removeStale(String subjectId, List<Document> dbDocuments, Map<String, String> onDiskHashes) {
        int removed = 0;
        for (Document document : dbDocuments) {
            if (onDiskHashes.containsKey(document.getFileName())) {
                continue;
            }
            embeddingStore.removeAll(new And(
                    new IsEqualTo("subject_id", subjectId),
                    new IsEqualTo("file_name", document.getFileName())));
            documentRepository.delete(document);
            removed++;
            log.info("Removed stale document {}/{}", subjectId, document.getFileName());
        }
        return removed;
    }

    private int ingestNewOrChanged(String subjectId, List<Document> dbDocuments, Map<String, String> onDiskHashes) {
        int ingested = 0;
        for (Map.Entry<String, String> entry : onDiskHashes.entrySet()) {
            String fileName = entry.getKey();
            String hash = entry.getValue();

            Document document = dbDocuments.stream()
                    .filter(d -> d.getFileName().equals(fileName))
                    .findFirst()
                    .orElse(null);

            if (document == null) {
                document = new Document(subjectId, fileName, subjectId + "/" + fileName, null);
                documentRepository.save(document);
            } else if (document.getStatus() == DocumentStatus.READY && hash.equals(document.getContentHash())) {
                continue;
            }

            ingestionService.ingest(document.getId());
            ingested++;
        }
        return ingested;
    }

    private Map<String, String> hashOnDiskFiles(Path subjectDir) {
        var pdfMatcher = FileSystems.getDefault().getPathMatcher("glob:**.pdf");
        var textMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{md,txt}");
        var pdfParser = new ApachePdfBoxDocumentParser();

        List<dev.langchain4j.data.document.Document> parsed = new ArrayList<>();
        parsed.addAll(FileSystemDocumentLoader.loadDocumentsRecursively(subjectDir, pdfMatcher, pdfParser));
        parsed.addAll(FileSystemDocumentLoader.loadDocumentsRecursively(subjectDir, textMatcher));

        Map<String, String> hashes = new HashMap<>();
        for (dev.langchain4j.data.document.Document doc : parsed) {
            String fileName = doc.metadata().getString(dev.langchain4j.data.document.Document.FILE_NAME);
            hashes.put(fileName, IngestionService.sha256(doc.text()));
        }
        return hashes;
    }
}
