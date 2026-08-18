package com.etka.veridoc.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Converts exceptions into JSON responses.
 *
 * <p>Messages describe what the client did wrong and never expose stack traces
 * or internal detail — on a service handling identity documents, an error
 * response is not the place to leak anything about how the system works.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(Map.of("error", failure.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception failure) {
        // Log the detail server-side; return nothing specific to the caller.
        System.err.println("Unhandled: " + failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Verification failed"));
    }
}