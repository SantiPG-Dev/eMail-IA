package com.emailai.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.emailai.domain.entities.Contacto;
import com.emailai.service.ContactoService;
import com.emailai.web.dto.ContactoRequest;
import com.emailai.web.dto.ContactoResponse;

@RestController
@RequestMapping("/api/contactos")
public class ContactoController {

    private final ContactoService contactoService;

    public ContactoController(ContactoService contactoService) {
        this.contactoService = contactoService;
    }

    @GetMapping
    public List<ContactoResponse> listar() {
        return contactoService.listarTodos().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ContactoResponse obtener(@PathVariable Integer id) {
        return toResponse(contactoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ContactoResponse> crear(@Valid @RequestBody ContactoRequest req) {
        Contacto c = new Contacto();
        c.setNombre(req.nombre());
        c.setApellidoCifrado(req.apellido());
        c.setEmailCifrado(req.email());
        c.setTelefonoCifrado(req.telefono());
        c.setNotasCifrado(req.notas());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(contactoService.guardar(c)));
    }

    @PutMapping("/{id}")
    public ContactoResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ContactoRequest req) {
        Contacto c = new Contacto();
        c.setNombre(req.nombre());
        c.setApellidoCifrado(req.apellido());
        c.setEmailCifrado(req.email());
        c.setTelefonoCifrado(req.telefono());
        c.setNotasCifrado(req.notas());
        return toResponse(contactoService.actualizar(id, c));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        contactoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private ContactoResponse toResponse(Contacto c) {
        // Nota: los campos cifrados se descifran en Fase 4 (SecureStorage)
        return new ContactoResponse(c.getId(), c.getNombre(),
                c.getApellidoCifrado(), c.getEmailCifrado(),
                c.getTelefonoCifrado(), c.getNotasCifrado());
    }
}
