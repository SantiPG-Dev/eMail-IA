package com.emailai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.domain.entities.Entrenamiento;
import com.emailai.repository.EntrenamientoRepository;

/**
 * Servicio de datos de entrenamiento para el clasificador Weka.
 *
 * <p>Almacena ejemplos etiquetados (legítimo/spam/phishing) que el filtro
 * usa para reentrenar el modelo Naive Bayes.
 */
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
