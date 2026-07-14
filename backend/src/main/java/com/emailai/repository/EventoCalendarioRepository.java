package com.emailai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.EventoCalendario;

public interface EventoCalendarioRepository extends JpaRepository<EventoCalendario, Integer> {

    List<EventoCalendario> findByFechaOrderByHoraAscIdAsc(String fecha);

    List<EventoCalendario> findByFechaInOrderByFechaAscHoraAscIdAsc(List<String> fechas);

    /** Fechas distintas que tienen eventos (para marcar días en el calendario). */
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT e.fecha FROM EventoCalendario e")
    List<String> findDistinctFechas();
}
