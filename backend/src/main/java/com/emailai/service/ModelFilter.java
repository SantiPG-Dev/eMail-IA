package com.emailai.service;

import java.io.ObjectInputFilter;

// Filtro de seguridad para deserialización de modelos Weka (JEP 290).
// Allowlist estricta: clases de Weka + lo mínimo del JDK que Weka serializa
// (colecciones,/lang,io,math,time,text y arrays). Se elimina el wildcard
// "java.*"/"javax.*"/"sun.*" anterior, que abría la puerta a gadgets de
// deserialización si alguien lograra escribir un .model malicioso.
public class ModelFilter implements ObjectInputFilter {
    @Override
    public Status checkInput(FilterInfo info) {
        Class<?> clazz = info.serialClass();
        if (clazz == null) return Status.UNDECIDED;

        String name = clazz.getName();
        if (name.startsWith("weka.")
            || name.startsWith("java.util.")
            || name.startsWith("java.lang.")
            || name.startsWith("java.io.")
            || name.startsWith("java.math.")
            || name.startsWith("java.time.")
            || name.startsWith("java.text.")
            || clazz.isArray()) {
            return Status.ALLOWED;
        }
        return Status.REJECTED;
    }
}
