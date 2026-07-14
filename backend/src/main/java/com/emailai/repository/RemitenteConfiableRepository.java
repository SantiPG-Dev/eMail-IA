package com.emailai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.RemitenteConfiable;

public interface RemitenteConfiableRepository extends JpaRepository<RemitenteConfiable, Integer> {

    Optional<RemitenteConfiable> findByRemitente(String remitente);

    boolean existsByRemitente(String remitente);
}
