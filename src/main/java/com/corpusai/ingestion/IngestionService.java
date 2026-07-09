package com.corpusai.ingestion;

import com.corpusai.document.Document;
import com.corpusai.document.DocumentNotFoundException;
import com.corpusai.document.DocumentRepository;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
public class IngestionService {

    private final DocumentRepository documentRepository;
    private final StorageProperties storageProperties;
    private final PgVectorEmbeddingStore embeddingStore;
    private final EmbeddingStoreIngestor ingestor;

    public IngestionService(DocumentRepository documentRepository,
                            StorageProperties storageProperties,
                            EmbeddingModel embeddingModel,
                            PgVectorEmbeddingStore embeddingStore) {
        this.documentRepository = documentRepository;
        this.storageProperties = storageProperties;
        this.embeddingStore = embeddingStore;
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 30))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    public Path resolvePath(Document document) {
        return Path.of(storageProperties.root())
                .resolve(document.getSubjectId())
                .resolve(document.getFileName());
    }

    /**
     * Parses, embeds and updates status for one document. Runs synchronously on the
     * calling thread; {@link #ingestAsync(UUID)} is the fire-and-forget wrapper used
     * by the upload endpoint.
     */
    public void ingest(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Unknown document: " + documentId));

        document.markIngesting();
        documentRepository.save(document);

        try {
            Path path = resolvePath(document);
            dev.langchain4j.data.document.Document parsed =
                    FileSystemDocumentLoader.loadDocument(path, parserFor(path));
            String contentHash = sha256(parsed.text());

            embeddingStore.removeAll(new And(
                    new IsEqualTo("subject_id", document.getSubjectId()),
                    new IsEqualTo("file_name", document.getFileName())));

            parsed.metadata().put("subject_id", document.getSubjectId());
            ingestor.ingest(parsed);

            document.markReady(contentHash);
            documentRepository.save(document);
            log.info("Ingested {}/{}", document.getSubjectId(), document.getFileName());
        } catch (Exception ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            document.markFailed(message);
            documentRepository.save(document);
            log.error("Failed to ingest {}/{}", document.getSubjectId(), document.getFileName(), ex);
        }
    }

    @Async
    public void ingestAsync(UUID documentId) {
        ingest(documentId);
    }

    public void delete(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        embeddingStore.removeAll(new And(
                new IsEqualTo("subject_id", document.getSubjectId()),
                new IsEqualTo("file_name", document.getFileName())));

        try {
            Files.deleteIfExists(resolvePath(document));
        } catch (IOException ex) {
            log.warn("Could not delete file on disk for document {}/{}: {}",
                    document.getSubjectId(), document.getFileName(), ex.getMessage());
        }

        documentRepository.delete(document);
    }

    private DocumentParser parserFor(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf") ? new ApachePdfBoxDocumentParser() : new TextDocumentParser();
    }

    static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
