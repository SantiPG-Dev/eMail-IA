package com.emailai.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.common.NotFoundException;
import com.emailai.domain.entities.Mensaje;
import com.emailai.repository.MensajeRepository;

/**
 * Servicio de mensajes de correo sincronizados por IMAP.
 *
 * <p>Implementa upsert (MERGE) por UID+cuenta+carpeta, listado paginado,
 * búsqueda en bandeja y limpieza de mensajes antiguos.
 */
@Service
@Transactional
public class MensajeService {

    private static final int MAX_MENSAJES_POR_CARPETA = 500;

    private final MensajeRepository repo;

    public MensajeService(MensajeRepository repo) {
        this.repo = repo;
    }

    /**
     * Guarda o actualiza un mensaje (upsert por UID+cuenta+carpeta).
     */
    public Mensaje guardarOActualizar(Mensaje mensaje) {
        Optional<Mensaje> existente = repo.findByUidAndCuentaHashAndCarpetaImap(
                mensaje.getUid(), mensaje.getCuentaHash(), mensaje.getCarpetaImap());
        if (existente.isPresent()) {
            Mensaje m = existente.get();
            m.setRemitente(mensaje.getRemitente());
            m.setDestinatarios(mensaje.getDestinatarios());
            m.setCc(mensaje.getCc());
            m.setCco(mensaje.getCco());
            m.setAsunto(mensaje.getAsunto());
            m.setCuerpo(mensaje.getCuerpo());
            m.setHtml(mensaje.getHtml());
            m.setCategoria(mensaje.getCategoria());
            m.setPrioridad(mensaje.getPrioridad());
            m.setFechaRecepcion(mensaje.getFechaRecepcion());
            return repo.save(m);
        }
        return repo.save(mensaje);
    }

    @Transactional(readOnly = true)
    public List<Mensaje> listarPorCarpeta(String cuentaHash, String carpetaImap) {
        return repo.findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc(
                cuentaHash, carpetaImap);
    }

    @Transactional(readOnly = true)
    public List<Mensaje> listarPaginado(String cuentaHash, String carpetaImap,
                                         int offset, int limite) {
        return repo.findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc(
                cuentaHash, carpetaImap, PageRequest.of(offset / limite, limite));
    }

    @Transactional(readOnly = true)
    public long contar(String cuentaHash, String carpetaImap) {
        return repo.countByCuentaHashAndCarpetaImap(cuentaHash, carpetaImap);
    }

    @Transactional(readOnly = true)
    public List<Mensaje> buscar(String cuentaHash, String carpetaImap, String filtro) {
        return repo.buscarEnBandeja(cuentaHash, carpetaImap, filtro);
    }

    @Transactional(readOnly = true)
    public Mensaje buscarPorId(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Mensaje", id));
    }

    public void eliminar(Long id) {
        repo.delete(buscarPorId(id));
    }

    /**
     * Obtiene todos los UIDs (Message-ID) locales para una cuenta+carpeta.
     */
    @Transactional(readOnly = true)
    public java.util.Set<String> listarUidsPorCarpeta(String cuentaHash, String carpetaImap) {
        return repo.findUidsByCuentaHashAndCarpetaImap(cuentaHash, carpetaImap);
    }

    /**
     * Elimina mensajes antiguos de una carpeta, dejando solo los N más recientes.
     */
    public void limpiarAntiguos(String cuentaHash, String carpetaImap) {
        repo.eliminarAntiguos(cuentaHash, carpetaImap, MAX_MENSAJES_POR_CARPETA);
    }
}
