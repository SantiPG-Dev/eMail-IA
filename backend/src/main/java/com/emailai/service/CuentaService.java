package com.emailai.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.common.NotFoundException;
import com.emailai.domain.entities.Cuenta;
import com.emailai.repository.CuentaRepository;

// CRUD de cuentas de correo. Credenciales cifradas en BD (SecureStorage).
@Service
@Transactional
public class CuentaService {

    private final CuentaRepository repo;

    public CuentaService(CuentaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Cuenta> listarTodas() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Cuenta> buscarPorEmail(String email) {
        return repo.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<Cuenta> buscarDefault() {
        return repo.findByEsDefaultTrue();
    }

    @Transactional(readOnly = true)
    public Cuenta buscarPorId(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cuenta", id));
    }

    public Cuenta guardar(Cuenta cuenta) {
        return repo.save(cuenta);
    }

    public void eliminar(Integer id) {
        repo.delete(buscarPorId(id));
    }

    /**
     * Marca una cuenta como default, desmarcando las demás.
     */
    public Cuenta marcarComoDefault(Integer id) {
        repo.findByEsDefaultTrue().ifPresent(d -> {
            d.setEsDefault(false);
            repo.save(d);
        });
        Cuenta c = buscarPorId(id);
        c.setEsDefault(true);
        return repo.save(c);
    }
}
