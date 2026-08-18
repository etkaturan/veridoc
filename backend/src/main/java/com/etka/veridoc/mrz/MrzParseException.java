package com.etka.veridoc.mrz;

/**
 * Thrown when input cannot be interpreted as a well-formed MRZ.
 *
 * <p>This signals a structural problem — wrong number of lines, wrong
 * line length, invalid characters. It does <em>not</em> signal a failed
 * check digit: an MRZ with a bad checksum is still structurally valid,
 * and is reported as a verification result rather than an exception.
 */
public class MrzParseException extends RuntimeException {

    public MrzParseException(String message) {
        super(message);
    }

    public MrzParseException(String message, Throwable cause) {
        super(message, cause);
    }
}