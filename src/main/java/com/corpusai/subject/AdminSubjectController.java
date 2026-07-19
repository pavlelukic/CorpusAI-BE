package com.corpusai.subject;

import com.corpusai.auth.UserSubjectAccessRepository;
import com.corpusai.subject.dto.AdminSubjectResponse;
import com.corpusai.subject.dto.CreateSubjectRequest;
import com.corpusai.subject.dto.UpdateSubjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/subjects")
public class AdminSubjectController {

    private final SubjectService subjectService;
    private final UserSubjectAccessRepository accessRepository;

    public AdminSubjectController(SubjectService subjectService, UserSubjectAccessRepository accessRepository) {
        this.subjectService = subjectService;
        this.accessRepository = accessRepository;
    }

    @GetMapping
    public List<AdminSubjectResponse> list() {
        return subjectService.listAllIncludingArchived().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSubjectResponse create(@Valid @RequestBody CreateSubjectRequest request) {
        Subject subject = subjectService.create(request.displayName(), request.displayNameSr(), request.systemPrompt());
        return toResponse(subject);
    }

    @PutMapping("/{subjectId}")
    public AdminSubjectResponse update(@PathVariable String subjectId, @Valid @RequestBody UpdateSubjectRequest request) {
        Subject subject = subjectService.update(subjectId, request.displayName(), request.displayNameSr(), request.systemPrompt());
        return toResponse(subject);
    }

    @DeleteMapping("/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable String subjectId) {
        subjectService.archive(subjectId);
        accessRepository.deleteAllByIdSubjectId(subjectId);
    }

    private AdminSubjectResponse toResponse(Subject subject) {
        return new AdminSubjectResponse(subject.getId(), subject.getDisplayName(), subject.getDisplayNameSr(),
                subject.getSystemPrompt(), subject.isArchived(), subject.getCreatedAt());
    }
}
