package com.schwab.audit.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CustomExceptionTest {

    @Test
    void badRequestExceptionPreservesMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid field");
        BadRequestException exception = new BadRequestException("Invalid request", cause);

        assertEquals("Invalid request", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void forbiddenExceptionPreservesMessageAndCause() {
        SecurityException cause = new SecurityException("missing role");
        ForbiddenException exception = new ForbiddenException("Access denied", cause);

        assertEquals("Access denied", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
