package com.emailai.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.common.NotFoundException;
import com.emailai.domain.entities.ItemAgenda;
import com.emailai.repository.ItemAgendaRepository;

// CRUD de la agenda unica: eventos y tareas viven en la misma tabla, de modo
// que lo creado en Calendario aparece en Tareas y al reves. El tipo solo
// cambia como se presenta y que integraciones le aplican (ICS todo, Todoist
// solo TAREA); nunca se modifica al editar.
@Service
@Transactional
public class AgendaService {

    private final ItemAgendaRepository repo;

    public AgendaService(ItemAgendaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ItemAgenda> listarCalendario() {
        return repo.findByFechaIsNotNullOrderByFechaAscHoraAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<ItemAgenda> listarTareas() {
        return repo.findAllForTareas();
    }

    @Transactional(readOnly = true)
    public List<ItemAgenda> listarPorFecha(LocalDate fecha) {
        return repo.findByFechaOrderByHoraAscIdAsc(fecha.toString());
    }

    @Transactional(readOnly = true)
    public List<String> fechasConItems() {
        return repo.findDistinctFechas();
    }

    @Transactional(readOnly = true)
    public ItemAgenda buscarPorId(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Elemento de agenda", id));
    }

    public ItemAgenda crearEvento(ItemAgenda item) {
        item.setTipo(ItemAgenda.EVENTO);
        if (item.getOrigen() == null) item.setOrigen("local");
        return repo.save(item);
    }

    public ItemAgenda crearTarea(ItemAgenda item) {
        item.setTipo(ItemAgenda.TAREA);
        if (item.getOrigen() == null) item.setOrigen("local");
        if (item.getEstado() == null) item.setEstado("pendiente");
        if (item.getPrioridad() == null) item.setPrioridad("MEDIA");
        return repo.save(item);
    }

    /** Guarda sin tocar el tipo (import de ICS, sync de Todoist...). */
    public ItemAgenda guardar(ItemAgenda item) {
        if (item.getTipo() == null) item.setTipo(ItemAgenda.TAREA);
        if (item.getOrigen() == null) item.setOrigen("local");
        return repo.save(item);
    }

    /** Edicion desde Calendario: no pisa estado/prioridad/etiquetas de una tarea. */
    public ItemAgenda actualizarEvento(Integer id, ItemAgenda datos) {
        ItemAgenda e = buscarPorId(id);
        e.setFecha(datos.getFecha());
        e.setHora(datos.getHora());
        e.setTodoElDia(datos.isTodoElDia());
        e.setFechaFin(datos.getFechaFin());
        e.setHoraFin(datos.getHoraFin());
        e.setTitulo(datos.getTitulo());
        e.setDetalle(datos.getDetalle());
        e.setMensajeId(datos.getMensajeId());
        return repo.save(e);
    }

    /** Edicion desde Tareas: no pisa tipo, origen, fechaFin ni el id de Todoist. */
    public ItemAgenda actualizarTarea(Integer id, ItemAgenda datos) {
        ItemAgenda t = buscarPorId(id);
        t.setTitulo(datos.getTitulo());
        t.setDetalle(datos.getDetalle());
        t.setFecha(datos.getFecha());
        t.setHora(datos.getHora());
        t.setTodoElDia(datos.isTodoElDia());
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
