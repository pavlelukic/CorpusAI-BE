package com.corpusai.subject;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class SubjectService {

    private static final String DEFAULT_TEMPLATE_PATH = "prompts/default-tutor-template.txt";

    private final SubjectRepository subjectRepository;
    private final String defaultPromptTemplate;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
        this.defaultPromptTemplate = loadDefaultTemplate();
    }

    public List<Subject> listActive() {
        return subjectRepository.findAllByArchivedFalseOrderByCreatedAtAsc();
    }

    public Subject findById(String subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subject: " + subjectId));
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
