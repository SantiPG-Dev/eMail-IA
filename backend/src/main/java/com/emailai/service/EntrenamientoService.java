package com.emailai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Entrenamiento;
import com.emailai.repository.EntrenamientoRepository;

// Almacena ejemplos etiquetados para reentrenar Weka.
@Service
@Transactional
public class EntrenamientoService {

    private final EntrenamientoRepository repo;

    public EntrenamientoService(EntrenamientoRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Entrenamiento> listarPorCuenta(String cuentaHash) {
        return repo.findByCuentaHash(cuentaHash);
    }

    @Transactional(readOnly = true)
    public List<Entrenamiento> listarPorCuentaYEtiqueta(String cuentaHash, String etiqueta) {
        return repo.findByCuentaHashAndEtiqueta(cuentaHash, etiqueta);
    }

    @Transactional(readOnly = true)
    public long contarPorEtiqueta(String cuentaHash, String etiqueta) {
        return repo.findByCuentaHashAndEtiqueta(cuentaHash, etiqueta).size();
    }

    public Entrenamiento guardar(Entrenamiento entrenamiento) {
        return repo.save(entrenamiento);
    }

    public void eliminar(Integer id) {
        repo.findById(id).ifPresent(repo::delete);
    }
}
