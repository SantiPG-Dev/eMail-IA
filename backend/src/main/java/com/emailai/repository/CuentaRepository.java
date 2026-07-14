package com.emailai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.Cuenta;

public interface CuentaRepository extends JpaRepository<Cuenta, Integer> {

    Optional<Cuenta> findByEmail(String email);

    Optional<Cuenta> findByEsDefaultTrue();
}
