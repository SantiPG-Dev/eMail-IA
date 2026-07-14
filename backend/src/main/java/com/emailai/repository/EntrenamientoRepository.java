package com.emailai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.Entrenamiento;

public interface EntrenamientoRepository extends JpaRepository<Entrenamiento, Integer> {

    List<Entrenamiento> findByCuentaHash(String cuentaHash);

    List<Entrenamiento> findByCuentaHashAndEtiqueta(String cuentaHash, String etiqueta);
}
