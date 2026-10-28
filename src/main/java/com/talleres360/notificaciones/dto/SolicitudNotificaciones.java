package com.talleres360.notificaciones.dto;

import java.time.Instant;

/** Resumen sin destinatarios ni cuerpos de mensajes, agrupado antes de paginar. */
public record SolicitudNotificaciones(Long orderId, long avisos, Instant ultimaActividad) {}
