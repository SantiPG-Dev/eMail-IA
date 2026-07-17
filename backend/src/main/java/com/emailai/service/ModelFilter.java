package com.emailai.service;

import java.io.ObjectInputFilter;

// Filtro de seguridad para deserialización de modelos Weka.
// Solo permite clases de Weka, JDK y tipos primitivos.
public class ModelFilter implements ObjectInputFilter {
    @Override
    public Status checkInput(FilterInfo info) {
        Class<?> clazz = info.serialClass();
        if (clazz == null) return Status.UNDECIDED;

        String name = clazz.getName();
        if (name.startsWith("weka.")
            || name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("sun.")
            || name.equals("[D") || name.equals("[F") || name.equals("[I")
            || name.equals("[Ljava.lang.Object;")
            || clazz.isArray()) {
            return Status.ALLOWED;
        }
        return Status.REJECTED;
    }
}
