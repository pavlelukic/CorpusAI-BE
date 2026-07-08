package com.corpusai.subject;

import com.corpusai.auth.AuthenticatedUser;
import com.corpusai.auth.SubjectAccessService;
import com.corpusai.subject.dto.SubjectResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;
    private final SubjectAccessService subjectAccessService;

    public SubjectController(SubjectService subjectService, SubjectAccessService subjectAccessService) {
        this.subjectService = subjectService;
        this.subjectAccessService = subjectAccessService;
    }

    @GetMapping
    public List<SubjectResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return subjectService.listActive().stream()
                .filter(s -> subjectAccessService.hasAccess(principal, s.getId()))
                .map(s -> new SubjectResponse(s.getId(), s.getDisplayName(), s.getDisplayNameSr()))
                .toList();
    }
}
