package com.talleres360.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class ConsumidorRabbit {
  private final ObjectMapper json;
  private final NotificacionService service;

  @Value("${app.rabbit.email-queue:q.cmd.email}")
  private String colaCorreo;

  @Value("${app.rabbit.workshop-queue:q.cmd.workshop}")
  private String colaTaller;

  @Value("${app.rabbit.invoice-queue:q.cmd.invoice}")
  private String colaComprobante;

  @RabbitListener(
      queues = {
        "${app.rabbit.email-queue:q.cmd.email}",
        "${app.rabbit.workshop-queue:q.cmd.workshop}",
        "${app.rabbit.invoice-queue:q.cmd.invoice}"
      },
      ackMode = "AUTO")
  public void consumir(byte[] contenido, @Header(AmqpHeaders.CONSUMER_QUEUE) String cola) {
    try {
      if (contenido.length > 65536)
        throw new IllegalArgumentException("Comando demasiado grande");
      var comando = json.readValue(contenido, ComandoNotificacion.class);
      if (comando == null || comando.tipo() == null)
        throw new IllegalArgumentException("Comando sin tipo");
      String esperada =
          switch (comando.tipo()) {
            case CORREO -> colaCorreo;
            case TICKET_TALLER -> colaTaller;
            case COMPROBANTE -> colaComprobante;
          };
      if (!esperada.equals(cola))
        throw new IllegalArgumentException("Tipo incompatible con la cola");
      // El proxy transaccional confirma la bandeja durable antes de volver al listener.
      // Los fallos de BD se propagan al contenedor, que aplica los reintentos configurados.
      service.recibir(comando);
    } catch (IllegalArgumentException | java.io.IOException error) {
      log.warn("Comando rechazado a DLQ en {}: {}", cola, error.getClass().getSimpleName());
      // Nunca retornar normalmente ante un comando inválido: AUTO lo confirmaría.
      throw new AmqpRejectAndDontRequeueException("Comando de notificación inválido");
    }
    // AUTO: Spring envía ACK solo al retornar correctamente. SMTP se procesa por separado.
  }
}
