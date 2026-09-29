package com.lingualoop.api.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "urn:lingualoop:problem:not-found", detail);
    }
}
