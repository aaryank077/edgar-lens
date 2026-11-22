package com.edgarlens.sec;

public class UnknownTickerException extends RuntimeException {
    public UnknownTickerException(String ticker) {
        super("No SEC registrant found for ticker '" + ticker + "'");
    }
}
