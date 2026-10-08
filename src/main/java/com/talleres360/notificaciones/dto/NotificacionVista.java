package com.talleres360.notificaciones.dto;

import com.talleres360.notificaciones.model.Notificacion;
import com.talleres360.notificaciones.model.TipoNotificacion;
import java.time.Instant;

public record NotificacionVista(
    String commandId,
    Long orderId,
    TipoNotificacion tipo,
    String destinatario,
    String asunto,
    String mensaje,
    Notificacion.Estado estado,
    Instant occurredAt,
    Instant proximoIntento,
    Instant procesadaEn,
    int intentos,
    String ultimoError) {
  public static NotificacionVista desde(Notificacion n) {
    return new NotificacionVista(
        n.getCommandId(),
        n.getOrderId(),
        n.getTipo(),
        n.getDestinatario(),
        n.getAsunto(),
        n.getMensaje(),
        n.getEstado(),
        n.getOccurredAt(),
        n.getProximoIntento(),
        n.getProcesadaEn(),
        n.getIntentos(),
        n.getUltimoError());
  }
}
