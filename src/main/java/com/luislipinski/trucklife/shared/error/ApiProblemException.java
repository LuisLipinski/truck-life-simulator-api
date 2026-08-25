package com.luislipinski.trucklife.shared.error;

import org.springframework.http.HttpStatus;

public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    public ApiProblemException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }
}
