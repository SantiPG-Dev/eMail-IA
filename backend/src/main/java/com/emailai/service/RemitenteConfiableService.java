package com.emailai.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.RemitenteConfiable;
import com.emailai.repository.RemitenteConfiableRepository;

/**
 * Servicio de la lista blanca de remitentes confiables.
 * El filtro spam nunca marca como spam los remitentes de esta lista.
 */
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
        return repo.existsByRemitente(remitente);
    }

    public void agregar(String remitente) {
        if (!repo.existsByRemitente(remitente)) {
            repo.save(new RemitenteConfiable(remitente));
        }
    }

    public void eliminar(String remitente) {
        repo.findByRemitente(remitente).ifPresent(repo::delete);
    }
}
