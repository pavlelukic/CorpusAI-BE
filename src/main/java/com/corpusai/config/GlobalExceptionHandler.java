package com.corpusai.config;

import com.corpusai.auth.DuplicateEmailException;
import com.corpusai.auth.UserNotFoundException;
import com.corpusai.chat.ChatSessionNotFoundException;
import com.corpusai.config.dto.ErrorResponse;
import com.corpusai.document.DocumentNotFoundException;
import com.corpusai.document.InvalidFileTypeException;
import com.corpusai.flashcards.FlashcardSetNotFoundException;
import com.corpusai.quiz.QuizAlreadyCompletedException;
import com.corpusai.quiz.QuizNotFoundException;
import com.corpusai.subject.DuplicateSubjectNameException;
import com.corpusai.subject.SubjectNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException ex) {
        return new ErrorResponse("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateEmail(DuplicateEmailException ex) {
        return new ErrorResponse("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(DuplicateSubjectNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateSubjectName(DuplicateSubjectNameException ex) {
        return new ErrorResponse("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleDocumentNotFound(DocumentNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleChatSessionNotFound(ChatSessionNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(SubjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSubjectNotFound(SubjectNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(FlashcardSetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleFlashcardSetNotFound(FlashcardSetNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(QuizNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleQuizNotFound(QuizNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(QuizAlreadyCompletedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleQuizAlreadyCompleted(QuizAlreadyCompletedException ex) {
        return new ErrorResponse("CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidFileType(InvalidFileTypeException ex) {
        return new ErrorResponse("BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ErrorResponse handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return new ErrorResponse("PAYLOAD_TOO_LARGE", "Uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthenticationException(AuthenticationException ex) {
        return new ErrorResponse("UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new ErrorResponse("FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleInternalState(IllegalStateException ex) {
        log.error("Internal state error", ex);
        String message = ex.getMessage() != null ? ex.getMessage()
                : ex.getCause() != null ? ex.getCause().getMessage()
                  : "Internal error";
        return new ErrorResponse("INTERNAL_ERROR", message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Invalid Request");
        return new ErrorResponse("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingBody(HttpMessageNotReadableException ex) {
        return new ErrorResponse("BAD_REQUEST", "Request body is missing or malformed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoResourceFoundException ex) {
        return new ErrorResponse("NOT_FOUND", "The requested resource was not found");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {
        log.error("Unhandled exception: {}", ex.getClass().getName(), ex);
        return new ErrorResponse("SERVER_ERROR", "An Unexpected Error occurred.");
    }
}
