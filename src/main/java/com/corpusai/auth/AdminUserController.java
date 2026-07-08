package com.corpusai.auth;

import com.corpusai.auth.dto.AdminUserResponse;
import com.corpusai.subject.SubjectService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final UserRepository userRepository;
    private final UserSubjectAccessRepository accessRepository;
    private final SubjectService subjectService;

    public AdminUserController(UserRepository userRepository,
                               UserSubjectAccessRepository accessRepository,
                               SubjectService subjectService) {
        this.userRepository = userRepository;
        this.accessRepository = accessRepository;
        this.subjectService = subjectService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/users/{userId}/subjects/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantAccess(@PathVariable UUID userId,
                            @PathVariable String subjectId,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));
        subjectService.findById(subjectId);

        UserSubjectId id = new UserSubjectId(user.getId(), subjectId);
        if (accessRepository.existsById(id)) {
            return;
        }
        accessRepository.save(new UserSubjectAccess(id, principal.id()));
    }

    @DeleteMapping("/users/{userId}/subjects/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAccess(@PathVariable UUID userId, @PathVariable String subjectId) {
        UserSubjectId id = new UserSubjectId(userId, subjectId);
        if (accessRepository.existsById(id)) {
            accessRepository.deleteById(id);
        }
    }

    private AdminUserResponse toResponse(User user) {
        List<String> subjectIds = accessRepository.findAllByIdUserId(user.getId()).stream()
                .map(access -> access.getId().getSubjectId())
                .toList();
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), subjectIds);
    }
}
