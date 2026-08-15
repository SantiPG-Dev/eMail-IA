package com.emailai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.Adjunto;

public interface AdjuntoRepository extends JpaRepository<Adjunto, Long> {

    List<Adjunto> findByMensajeId(Long mensajeId);

    Optional<Adjunto> findByIdAndMensajeId(Long id, Long mensajeId);
}
