package com.emailai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.emailai.domain.entities.Mensaje;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    /** Lista mensajes de una cuenta+carpeta ordenados por fecha descendente. */
    List<Mensaje> findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc(
            String cuentaHash, String carpetaImap);

    /** Lista mensajes paginados de una cuenta+carpeta. */
    List<Mensaje> findByCuentaHashAndCarpetaImapOrderByFechaRecepcionDescIdDesc(
            String cuentaHash, String carpetaImap, Pageable pageable);

    /** Cuenta mensajes de una cuenta+carpeta (para paginación). */
    long countByCuentaHashAndCarpetaImap(String cuentaHash, String carpetaImap);

    /** Busca un mensaje por su UID único (cuenta+carpeta+uid). */
    Optional<Mensaje> findByUidAndCuentaHashAndCarpetaImap(
            String uid, String cuentaHash, String carpetaImap);

    /** Busca mensajes por asunto o remitente (filtro de bandeja). */
    @Query("SELECT m FROM Mensaje m WHERE m.cuentaHash = :cuentaHash " +
           "AND m.carpetaImap = :carpetaImap " +
           "AND (LOWER(m.asunto) LIKE LOWER(CONCAT('%', :filtro, '%')) " +
           "  OR LOWER(m.remitente) LIKE LOWER(CONCAT('%', :filtro, '%'))) " +
           "ORDER BY m.fechaRecepcion DESC, m.id DESC")
    List<Mensaje> buscarEnBandeja(@Param("cuentaHash") String cuentaHash,
                                   @Param("carpetaImap") String carpetaImap,
                                   @Param("filtro") String filtro);

    /** Elimina los mensajes más antiguos de una carpeta, dejando solo los N más recientes. */
    @Modifying
    @Query(value = "DELETE FROM mensajes WHERE cuenta_hash = :cuentaHash " +
           "AND carpeta_imap = :carpetaImap AND id NOT IN (" +
           "  SELECT id FROM (SELECT id FROM mensajes " +
           "    WHERE cuenta_hash = :cuentaHash AND carpeta_imap = :carpetaImap " +
           "    ORDER BY fecha_recepcion DESC, id DESC LIMIT :limite) AS t" +
           ")", nativeQuery = true)
    void eliminarAntiguos(@Param("cuentaHash") String cuentaHash,
                          @Param("carpetaImap") String carpetaImap,
                          @Param("limite") int limite);
}
