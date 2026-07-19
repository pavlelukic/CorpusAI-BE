package com.corpusai.subject;

import com.corpusai.ingestion.StorageProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class SubjectService {

    private static final String DEFAULT_TEMPLATE_PATH = "prompts/default-tutor-template.txt";

    private final SubjectRepository subjectRepository;
    private final StorageProperties storageProperties;
    private final String defaultPromptTemplate;

    public SubjectService(SubjectRepository subjectRepository, StorageProperties storageProperties) {
        this.subjectRepository = subjectRepository;
        this.storageProperties = storageProperties;
        this.defaultPromptTemplate = loadDefaultTemplate();
    }

    public List<Subject> listActive() {
        return subjectRepository.findAllByArchivedFalseOrderByCreatedAtAsc();
    }

    public List<Subject> listAllIncludingArchived() {
        return subjectRepository.findAllByOrderByCreatedAtAsc();
    }

    public Subject findById(String subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
    }

    public Subject create(String displayName, String displayNameSr, String systemPrompt) {
        String slug = SlugGenerator.slugify(displayName);
        if (subjectRepository.existsById(slug)) {
            throw new DuplicateSubjectNameException(displayName);
        }

        Subject subject = new Subject(slug, displayName, displayNameSr, systemPrompt);
        subjectRepository.save(subject);
        createStorageDirectory(slug);
        return subject;
    }

    public Subject update(String subjectId, String displayName, String displayNameSr, String systemPrompt) {
        Subject subject = findById(subjectId);
        subject.updateDetails(displayName, displayNameSr, systemPrompt);
        return subjectRepository.save(subject);
    }

    public void archive(String subjectId) {
        Subject subject = findById(subjectId);
        subject.archive();
        subjectRepository.save(subject);
    }

    private void createStorageDirectory(String subjectId) {
        try {
            Files.createDirectories(Path.of(storageProperties.root()).resolve(subjectId));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create storage directory for subject: " + subjectId, ex);
        }
    }

    public String systemPromptFor(Subject subject) {
        String overridePrompt = subject.getSystemPrompt();
        if(overridePrompt != null && !overridePrompt.isBlank()) {
            return overridePrompt;
        }
        return defaultPromptTemplate.replace("{{subjectName}}", subject.getDisplayName());
    }

    public String loadDefaultTemplate() {
        try{
            return new ClassPathResource(DEFAULT_TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load default prompt template: " +
                    DEFAULT_TEMPLATE_PATH, ex);
        }
    }
}
