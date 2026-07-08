package com.corpusai.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class SubjectAccessService {

    private final UserSubjectAccessRepository accessRepository;

    public SubjectAccessService(UserSubjectAccessRepository accessRepository) {
        this.accessRepository = accessRepository;
    }

    public void checkAccess(AuthenticatedUser principal, String subjectId) {
        if (!hasAccess(principal, subjectId)) {
            throw new AccessDeniedException("You do not have access to this subject: " + subjectId);
        }
    }

    public boolean hasAccess(AuthenticatedUser principal, String subjectId) {
        return principal.role() == Role.ADMIN
                || accessRepository.existsByIdUserIdAndIdSubjectId(principal.id(), subjectId);
    }
}
