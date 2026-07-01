package com.corpusai.subject;

import com.corpusai.config.SubjectsProperties;
import com.corpusai.subject.dto.SubjectResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectsProperties subjectsProperties;

    public SubjectController(SubjectsProperties subjectsProperties) {
        this.subjectsProperties = subjectsProperties;
    }

    @GetMapping
    public List<SubjectResponse> list() {
        return subjectsProperties.subjects().stream()
                .map(s -> new SubjectResponse(s.id(), s.displayName(), s.displayNameSr()))
                .toList();
    }
}
