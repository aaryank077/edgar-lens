package com.edgarlens.valuation;

public class InsufficientDataException extends RuntimeException {
    public InsufficientDataException(String message) {
        super(message);
    }
}
