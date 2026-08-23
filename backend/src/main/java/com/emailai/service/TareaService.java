package com.emailai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.common.NotFoundException;
import com.emailai.domain.entities.Tarea;
import com.emailai.repository.TareaRepository;

// CRUD de tareas, ordenadas por fecha de vencimiento.
@Service
@Transactional
public class TareaService {

    private final TareaRepository repo;

    public TareaService(TareaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Tarea> listarTodas() {
        return repo.findAllOrderByFechaVencimiento();
    }

    @Transactional(readOnly = true)
    public Tarea buscarPorId(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Tarea", id));
    }

    public Tarea guardar(Tarea tarea) {
        if (tarea.getPrioridad() == null) {
            tarea.setPrioridad("MEDIA");
        }
        return repo.save(tarea);
    }

    public Tarea actualizar(Integer id, Tarea datos) {
        Tarea t = buscarPorId(id);
        t.setTitulo(datos.getTitulo());
        t.setDescripcion(datos.getDescripcion());
        t.setFechaVencimiento(datos.getFechaVencimiento());
        t.setEstado(datos.getEstado());
        t.setEtiquetas(datos.getEtiquetas());
        t.setPrioridad(datos.getPrioridad() != null ? datos.getPrioridad() : "MEDIA");
        t.setMensajeId(datos.getMensajeId());
        return repo.save(t);
    }

    public void eliminar(Integer id) {
        repo.delete(buscarPorId(id));
    }
}
