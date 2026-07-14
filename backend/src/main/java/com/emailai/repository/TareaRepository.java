package com.emailai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.emailai.domain.entities.Tarea;

public interface TareaRepository extends JpaRepository<Tarea, Integer> {

    /** Lista tareas ordenadas: sin fecha al final, luego por fecha ascendente. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT t FROM Tarea t ORDER BY CASE WHEN t.fechaVencimiento IS NULL THEN 1 ELSE 0 END, t.fechaVencimiento")
    List<Tarea> findAllOrderByFechaVencimiento();
}
