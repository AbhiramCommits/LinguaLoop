package com.lingualoop.api.common.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "urn:lingualoop:problem:bad-request", detail);
    }
}
