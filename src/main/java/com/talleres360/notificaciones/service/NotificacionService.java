package com.talleres360.notificaciones.service;

import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.model.Notificacion;
import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.repository.NotificacionRepository;
import jakarta.validation.Validator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificacionService {
  private final NotificacionRepository repository;
  private final Validator validator;

  @Transactional
  public void recibir(ComandoNotificacion input) {
    if (!validator.validate(input).isEmpty()
        || (input.tipo() == TipoNotificacion.CORREO
            && (input.destinatario() == null || input.destinatario().isBlank())))
      throw new IllegalArgumentException("Comando inválido");
    String identificador = java.util.UUID.fromString(input.commandId()).toString();
    var anterior = repository.findById(identificador);
    if (anterior.isPresent()) {
      var n = anterior.get();
      if (!n.getOrderId().equals(input.orderId())
          || n.getTipo() != input.tipo()
          || !java.util.Objects.equals(n.getDestinatario(), input.destinatario())
          || !n.getAsunto().equals(input.asunto())
          || !n.getMensaje().equals(input.mensaje())
          || !n.getOccurredAt()
              .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
              .equals(input.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))
        throw new IllegalArgumentException("El identificador ya corresponde a otro comando");
      return;
    }
    var n = new Notificacion();
    n.setCommandId(identificador);
    n.setOrderId(input.orderId());
    n.setTipo(input.tipo());
    n.setDestinatario(input.destinatario());
    n.setAsunto(input.asunto());
    n.setMensaje(input.mensaje());
    n.setOccurredAt(input.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    repository.saveAndFlush(n);
  }

  @Transactional(readOnly = true)
  public Page<Notificacion> listar(Long orderId, boolean soloTickets, int pagina, int tamano) {
    return repository.findAll(
        (root, query, cb) -> {
          var condiciones = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          if (orderId != null) condiciones.add(cb.equal(root.get("orderId"), orderId));
          if (soloTickets)
            condiciones.add(cb.equal(root.get("tipo"), TipoNotificacion.TICKET_TALLER));
          return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },
        PageRequest.of(
            pagina, tamano, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by("commandId"))));
  }

  @Transactional
  public void reintentar(String id) {
    var n =
        repository
            .bloquear(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notificación no encontrada"));
    if (n.getEstado() != Estado.FALLIDA)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Solo se reintentan notificaciones fallidas");
    n.setEstado(Estado.PENDIENTE);
    n.setIntentos(0);
    n.setProximoIntento(Instant.now());
    n.setUltimoError(null);
  }
}
