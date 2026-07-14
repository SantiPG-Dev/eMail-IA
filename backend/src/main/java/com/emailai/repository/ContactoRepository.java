package com.emailai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.Contacto;

public interface ContactoRepository extends JpaRepository<Contacto, Integer> {
}
