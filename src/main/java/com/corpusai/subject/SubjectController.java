package com.corpusai.subject;

import com.corpusai.subject.dto.SubjectResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public List<SubjectResponse> list() {
        return subjectService.listActive().stream()
                .map(s -> new SubjectResponse(s.getId(), s.getDisplayName(), s.getDisplayNameSr()))
                .toList();
    }
}
