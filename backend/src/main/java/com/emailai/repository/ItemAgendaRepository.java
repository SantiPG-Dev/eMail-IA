package com.emailai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.emailai.domain.entities.ItemAgenda;

public interface ItemAgendaRepository extends JpaRepository<ItemAgenda, Integer> {

    /** Calendario: solo lo que tiene fecha (una tarea sin fecha no se puede pintar). */
    List<ItemAgenda> findByFechaIsNotNullOrderByFechaAscHoraAscIdAsc();

    List<ItemAgenda> findByFechaOrderByHoraAscIdAsc(String fecha);

    /** Tareas: todo, con las que no tienen fecha al final. */
    @Query("SELECT i FROM ItemAgenda i "
         + "ORDER BY CASE WHEN i.fecha IS NULL THEN 1 ELSE 0 END, i.fecha, i.hora, i.id")
    List<ItemAgenda> findAllForTareas();

    /** Fechas distintas con elementos (marcar dias en el calendario). */
    @Query("SELECT DISTINCT i.fecha FROM ItemAgenda i WHERE i.fecha IS NOT NULL")
    List<String> findDistinctFechas();
}
