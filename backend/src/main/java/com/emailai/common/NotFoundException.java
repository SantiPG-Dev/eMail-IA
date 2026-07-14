package com.emailai.common;

/**
 * Excepción lanzada cuando no se encuentra un recurso por ID.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String recurso, Object id) {
        super(recurso + " no encontrado: " + id);
    }
}
