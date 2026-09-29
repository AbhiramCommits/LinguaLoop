package com.lingualoop.api.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, "urn:lingualoop:problem:unauthorized", detail);
    }
}
