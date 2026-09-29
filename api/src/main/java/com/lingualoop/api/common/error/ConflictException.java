package com.lingualoop.api.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, "urn:lingualoop:problem:conflict", detail);
    }
}
