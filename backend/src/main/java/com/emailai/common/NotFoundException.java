package com.emailai.common;

// Recurso no encontrado por ID (HTTP 404).
public class NotFoundException extends RuntimeException {
    public NotFoundException(String recurso, Object id) {
        super(recurso + " no encontrado: " + id);
    }
}
