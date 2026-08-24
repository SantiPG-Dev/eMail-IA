package com.emailai.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.RemitenteConfiable;
import com.emailai.repository.RemitenteConfiableRepository;

// Clasificación por remitente: LEGITIMO (lista blanca original) o SPAM/PHISHING.
@Service
@Transactional
public class RemitenteConfiableService {

    private final RemitenteConfiableRepository repo;

    public RemitenteConfiableService(RemitenteConfiableRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Set<String> listarTodos() {
        return repo.findAll().stream()
                .map(RemitenteConfiable::getRemitente)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean esConfiable(String remitente) {
        return repo.findByRemitente(remitente)
                .map(r -> "LEGITIMO".equals(r.getCategoria()))
                .orElse(false);
    }

    /** Categoría manual del remitente (vacio si no está clasificado). */
    @Transactional(readOnly = true)
    public java.util.Optional<String> categoriaDe(String remitente) {
        return repo.findByRemitente(remitente).map(RemitenteConfiable::getCategoria);
    }

    public void agregar(String remitente) {
        agregar(remitente, "LEGITIMO");
    }

    /** Upsert: si el remitente ya existía, actualiza su categoría. */
    public void agregar(String remitente, String categoria) {
        RemitenteConfiable r = repo.findByRemitente(remitente).orElseGet(() -> {
            RemitenteConfiable nuevo = new RemitenteConfiable(remitente);
            return repo.save(nuevo);
        });
        r.setCategoria(categoria);
        repo.save(r);
    }

    public void eliminar(String remitente) {
        repo.findByRemitente(remitente).ifPresent(repo::delete);
    }
}
