package com.emailai.web.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.emailai.domain.entities.Mensaje;
import com.emailai.service.MailService;
import com.emailai.service.MensajeService;
import com.emailai.web.dto.MensajeListResponse;
import com.emailai.web.dto.MensajeResponse;

@RestController
@RequestMapping("/api/mensajes")
public class MensajeController {

    private final MensajeService mensajeService;
    private final MailService mailService;

    public MensajeController(MensajeService mensajeService, MailService mailService) {
        this.mensajeService = mensajeService;
        this.mailService = mailService;
    }

    @GetMapping
    public MensajeListResponse listar(
            @RequestParam String cuentaHash,
            @RequestParam(defaultValue = "INBOX") String carpeta,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limite) {
        var lista = mensajeService.listarPaginado(cuentaHash, carpeta, offset, limite);
        long total = mensajeService.contar(cuentaHash, carpeta);
        return new MensajeListResponse(
                lista.stream().map(this::toResponse).toList(),
                total, offset / limite, limite);
    }

    @GetMapping("/buscar")
    public MensajeListResponse buscar(
            @RequestParam String cuentaHash,
            @RequestParam(defaultValue = "INBOX") String carpeta,
            @RequestParam String q) {
        var lista = mensajeService.buscar(cuentaHash, carpeta, q);
        return new MensajeListResponse(
                lista.stream().map(this::toResponse).toList(),
                lista.size(), 0, lista.size());
    }

    @GetMapping("/{id}")
    public MensajeResponse obtener(@PathVariable Long id) {
        return toResponse(mensajeService.buscarPorId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        mensajeService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clasificar")
    public MensajeResponse clasificar(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        m = mailService.clasificarMensaje(m);
        mensajeService.guardarOActualizar(m);
        return toResponse(m);
    }

    @PostMapping("/{id}/resumen")
    public String resumen(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        return mailService.generarResumen(m);
    }

    @PostMapping("/{id}/sugerir")
    public String sugerir(@PathVariable Long id) {
        Mensaje m = mensajeService.buscarPorId(id);
        return mailService.sugerirRespuesta(m);
    }

    private MensajeResponse toResponse(Mensaje m) {
        return new MensajeResponse(m.getId(), m.getUid(), m.getCuentaHash(),
                m.getCarpetaImap(), m.getRemitente(), m.getDestinatarios(),
                m.getCc(), m.getCco(), m.getAsunto(), m.getCuerpo(),
                m.getHtml(), m.getCategoria(), m.getPrioridad(), m.getFechaRecepcion());
    }
}
