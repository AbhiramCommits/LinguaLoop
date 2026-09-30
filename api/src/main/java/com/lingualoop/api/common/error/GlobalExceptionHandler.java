package com.lingualoop.api.common.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException ex) {
        return build(ex.getStatus(), URI.create(ex.getProblemType()), ex.getMessage());
    }

    @ExceptionHandler(com.lingualoop.api.admin.imports.ImportValidationException.class)
    ProblemDetail handleImportValidation(com.lingualoop.api.admin.imports.ImportValidationException ex) {
        ProblemDetail pd = build(HttpStatus.BAD_REQUEST,
                URI.create("urn:lingualoop:problem:import-validation"),
                ex.getMessage());
        pd.setProperty("rowErrors", ex.getErrors().stream()
                .map(error -> java.util.Map.of("line", error.line(), "message", error.message()))
                .toList());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage()));
        String detail = fieldErrors.values().stream().findFirst().orElse("Request validation failed");
        ProblemDetail pd = build(HttpStatus.BAD_REQUEST, URI.create("urn:lingualoop:problem:validation"), detail);
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, URI.create("urn:lingualoop:problem:bad-request"),
                "Request body is malformed");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, URI.create("urn:lingualoop:problem:not-found"),
                "Resource not found: " + ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = build(HttpStatus.INTERNAL_SERVER_ERROR,
                URI.create("urn:lingualoop:problem:internal"), "An unexpected error occurred");
        pd.setProperty("exception", ex.getClass().getSimpleName());
        return pd;
    }

    private ProblemDetail build(HttpStatus status, URI type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(type);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
