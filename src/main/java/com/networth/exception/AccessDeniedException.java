package com.networth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user tries to access a resource they don't own.
 * Returns 404 (not 403) to prevent information leakage about resource existence.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String resourceType, String id) {
        // Return same message as not found to avoid leaking that the resource exists
        super(String.format("%s with id '%s' not found", resourceType, id));
    }
}
