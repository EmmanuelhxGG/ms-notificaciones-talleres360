package com.talleres360.notificaciones.service;

import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.dto.CorreoManual;
import com.talleres360.notificaciones.dto.SolicitudNotificaciones;
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
  public void recibirManual(CorreoManual entrada, String mensajeId) {
    if (!validator.validate(entrada).isEmpty()) throw new IllegalArgumentException("Correo manual inválido");
    String email = entrada.email().toLowerCase(java.util.Locale.ROOT);
    // Sin message_id, repetir exactamente los tres campos representa la misma tarea.
    String identidad = mensajeId == null || mensajeId.isBlank()
        ? "correo-manual:v1\u0000" + email + "\u0000" + entrada.asunto() + "\u0000" + entrada.mensaje()
        : "correo-manual-id:v1\u0000" + mensajeId;
    String id = java.util.UUID.nameUUIDFromBytes(identidad.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    Instant fecha = repository.findById(id).map(Notificacion::getOccurredAt).orElseGet(Instant::now);
    recibir(new ComandoNotificacion(id, null, TipoNotificacion.CORREO, email,
        entrada.asunto(), entrada.mensaje(), fecha, null));
  }

  @Transactional
  public void recibir(ComandoNotificacion input) {
    if (!validator.validate(input).isEmpty()
        || (input.orderId() == null && input.tipo() != TipoNotificacion.CORREO)
        || (input.tipo() == TipoNotificacion.CORREO
            && (input.destinatario() == null || input.destinatario().isBlank()))
        || (input.comprobanteId() != null && input.tipo() != TipoNotificacion.CORREO))
      throw new IllegalArgumentException("Comando inválido");
    String identificador = java.util.UUID.fromString(input.commandId()).toString();
    var anterior = repository.findById(identificador);
    if (anterior.isPresent()) {
      var n = anterior.get();
      if (!java.util.Objects.equals(n.getOrderId(), input.orderId())
          || n.getTipo() != input.tipo()
          || !java.util.Objects.equals(n.getDestinatario(), input.destinatario())
          || !n.getAsunto().equals(input.asunto())
          || !n.getMensaje().equals(input.mensaje())
          || !java.util.Objects.equals(n.getComprobanteId(), normalizar(input.comprobanteId()))
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
    n.setComprobanteId(normalizar(input.comprobanteId()));
    n.setOccurredAt(input.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    repository.saveAndFlush(n);
  }

  private static String normalizar(String id) {
    return id == null ? null : java.util.UUID.fromString(id).toString();
  }

  @Transactional(readOnly=true)
  public Page<SolicitudNotificaciones> solicitudes(
      Long orderId, boolean operador, String correo, int pagina, int tamano) {
    if (!operador && (correo==null || correo.isBlank()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return repository.solicitudes(
        operador ? java.util.List.of(TipoNotificacion.CORREO)
            : java.util.List.of(TipoNotificacion.CORREO,TipoNotificacion.COMPROBANTE),
        operador ? null : correo.trim().toLowerCase(java.util.Locale.ROOT),
        orderId,PageRequest.of(pagina,tamano));
  }

  @Transactional(readOnly = true)
  public Page<Notificacion> listar(Long orderId, boolean soloCorreos, int pagina, int tamano) {
    return listar(orderId, soloCorreos, null, pagina, tamano);
  }

  @Transactional(readOnly = true)
  public Page<Notificacion> listar(
      Long orderId, boolean soloCorreos, String correo, int pagina, int tamano) {
    return listar(orderId, soloCorreos, correo, pagina, tamano, false);
  }

  @Transactional(readOnly = true)
  public Page<Notificacion> listar(
      Long orderId, boolean soloCorreos, String correo, int pagina, int tamano, boolean generales) {
    return repository.findAll(
        (root, query, cb) -> {
          var condiciones = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          if (orderId != null) condiciones.add(cb.equal(root.get("orderId"), orderId));
          if (generales) condiciones.add(cb.isNull(root.get("orderId")));
          if (soloCorreos)
            condiciones.add(cb.equal(root.get("tipo"), TipoNotificacion.CORREO));
          if (correo != null) {
            condiciones.add(
                root.get("tipo").in(TipoNotificacion.CORREO, TipoNotificacion.COMPROBANTE));
            condiciones.add(
                cb.equal(
                    cb.lower(root.get("destinatario")),
                    correo.trim().toLowerCase(java.util.Locale.ROOT)));
          }
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
    n.setFalloPublicado(false);
  }
}
