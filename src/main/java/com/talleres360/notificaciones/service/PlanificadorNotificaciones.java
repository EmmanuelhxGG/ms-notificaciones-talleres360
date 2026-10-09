package com.talleres360.notificaciones.service;

import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.repository.NotificacionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PlanificadorNotificaciones {
  private final NotificacionRepository repository;
  private final ProcesadorNotificaciones procesador;

  @Value("${app.smtp.enabled:false}")
  private boolean incluirCorreos;

  @Scheduled(fixedDelayString = "${NOTIFICATIONS_POLL_MS:3000}")
  public void procesarPendientes() {
    for (String id :
        repository.pendientes(
            List.of(Estado.PENDIENTE, Estado.REINTENTO),
            Instant.now(),
            incluirCorreos,
            TipoNotificacion.CORREO,
            PageRequest.of(0, 50))) {
      try {
        procesador.procesar(id);
      } catch (RuntimeException error) {
        // Un registro con problemas no debe detener el lote ni revelar datos del correo.
        log.warn("No se pudo procesar el comando {} ({})", id, error.getClass().getSimpleName());
      }
    }
  }
}
