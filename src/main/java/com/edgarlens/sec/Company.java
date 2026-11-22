package com.edgarlens.sec;

/** A registrant as listed in SEC's ticker file. CIK is zero-padded to 10 digits. */
public record Company(String ticker, String cik, String name) {
}
