package com.pki.ca.caservice.exception;

public class CaException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public CaException(String message) {
        super(message);
    }

    public CaException(String message, Throwable cause) {
        super(message, cause);
    }
}
