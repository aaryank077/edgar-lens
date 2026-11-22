package com.edgarlens.sec;

public class SecUnavailableException extends RuntimeException {
    public SecUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecUnavailableException(String message) {
        super(message);
    }
}
