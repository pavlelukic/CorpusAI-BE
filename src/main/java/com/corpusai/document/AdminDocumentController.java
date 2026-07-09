package com.corpusai.document;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.document.dto.DocumentResponse;
import com.corpusai.ingestion.IngestionService;
import com.corpusai.ingestion.StorageProperties;
import com.corpusai.subject.SubjectService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminDocumentController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "md", "txt");

    private final SubjectService subjectService;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final StorageProperties storageProperties;

    public AdminDocumentController(SubjectService subjectService,
                                   DocumentRepository documentRepository,
                                   IngestionService ingestionService,
                                   StorageProperties storageProperties) {
        this.subjectService = subjectService;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.storageProperties = storageProperties;
    }

    @PostMapping("/subjects/{subjectId}/documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(@PathVariable String subjectId,
                                   @RequestParam("file") MultipartFile file,
                                   @AuthenticationPrincipal AuthenticatedUser principal) {
        subjectService.findById(subjectId);

        String fileName = sanitizeFileName(file.getOriginalFilename());
        validateFileType(fileName);

        Path path = Path.of(storageProperties.root()).resolve(subjectId).resolve(fileName);
        try {
            Files.createDirectories(path.getParent());
            file.transferTo(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store uploaded file: " + fileName, ex);
        }

        Document document = documentRepository.findBySubjectIdAndFileName(subjectId, fileName)
                .orElseGet(() -> new Document(subjectId, fileName, subjectId + "/" + fileName, principal.id()));
        document.markPending();
        documentRepository.save(document);

        ingestionService.ingestAsync(document.getId());
        return toResponse(document);
    }

    @GetMapping("/subjects/{subjectId}/documents")
    public List<DocumentResponse> list(@PathVariable String subjectId) {
        subjectService.findById(subjectId);
        return documentRepository.findAllBySubjectIdOrderByUploadedAtDesc(subjectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID documentId) {
        ingestionService.delete(documentId);
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileTypeException("Uploaded file has no name");
        }
        try {
            return Path.of(originalFilename).getFileName().toString();
        } catch (InvalidPathException ex) {
            throw new InvalidFileTypeException("Uploaded file has an invalid name");
        }
    }

    private void validateFileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileTypeException("Unsupported file type: " + fileName + " (allowed: pdf, md, txt)");
        }
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(document.getId(), document.getFileName(), document.getStatus(),
                document.getUploadedAt(), document.getErrorMessage());
    }
}
