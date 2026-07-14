package com.emailai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.common.NotFoundException;
import com.emailai.domain.entities.Contacto;
import com.emailai.repository.ContactoRepository;

/**
 * Servicio CRUD de contactos.
 *
 * <p>Los campos sensibles (apellido, email, teléfono, notas) se almacenan
 * cifrados en la BD (columnas *_cifrado). El cifrado/descifrado se realiza
 * en la capa de seguridad (Fase 4) vía SecureStorage; este servicio opera
 * sobre los valores ya cifrados que llegan del controller.
 */
@Service
@Transactional
public class ContactoService {

    private final ContactoRepository repo;

    public ContactoService(ContactoRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Contacto> listarTodos() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Contacto buscarPorId(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Contacto", id));
    }

    public Contacto guardar(Contacto contacto) {
        return repo.save(contacto);
    }

    public Contacto actualizar(Integer id, Contacto datos) {
        Contacto c = buscarPorId(id);
        c.setNombre(datos.getNombre());
        c.setApellidoCifrado(datos.getApellidoCifrado());
        c.setEmailCifrado(datos.getEmailCifrado());
        c.setTelefonoCifrado(datos.getTelefonoCifrado());
        c.setNotasCifrado(datos.getNotasCifrado());
        return repo.save(c);
    }

    public void eliminar(Integer id) {
        repo.delete(buscarPorId(id));
    }
}
