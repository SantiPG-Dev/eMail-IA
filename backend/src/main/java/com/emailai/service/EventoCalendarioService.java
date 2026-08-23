package com.emailai.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.emailai.common.NotFoundException;
import com.emailai.domain.entities.EventoCalendario;
import com.emailai.repository.EventoCalendarioRepository;

// CRUD de eventos del calendario.
@Service
@Transactional
public class EventoCalendarioService {

    private final EventoCalendarioRepository repo;

    public EventoCalendarioService(EventoCalendarioRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<EventoCalendario> listarTodos() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public List<EventoCalendario> listarPorFecha(LocalDate fecha) {
        return repo.findByFechaOrderByHoraAscIdAsc(fecha.toString());
    }

    @Transactional(readOnly = true)
    public List<String> fechasConEventos() {
        return repo.findDistinctFechas();
    }

    @Transactional(readOnly = true)
    public EventoCalendario buscarPorId(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Evento", id));
    }

    public EventoCalendario guardar(EventoCalendario evento) {
        if (evento.getOrigen() == null) {
            evento.setOrigen("local");
        }
        return repo.save(evento);
    }

    public EventoCalendario actualizar(Integer id, EventoCalendario datos) {
        EventoCalendario e = buscarPorId(id);
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

    public void eliminar(Integer id) {
        repo.delete(buscarPorId(id));
    }
}
