package com.lingualoop.api.common.error;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String problemType;

    protected ApiException(HttpStatus status, String problemType, String detail) {
        super(detail);
        this.status = status;
        this.problemType = problemType;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getProblemType() {
        return problemType;
    }
}
