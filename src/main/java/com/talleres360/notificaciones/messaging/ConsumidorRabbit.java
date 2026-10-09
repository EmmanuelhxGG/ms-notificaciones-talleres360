package com.talleres360.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
      ackMode = "MANUAL")
  public void consumir(Message mensaje, Channel canal) throws java.io.IOException {
    long entrega = mensaje.getMessageProperties().getDeliveryTag();
    try {
      if (mensaje.getBody().length > 65536)
        throw new IllegalArgumentException("Comando demasiado grande");
      var comando = json.readValue(mensaje.getBody(), ComandoNotificacion.class);
      String esperada =
          switch (comando.tipo()) {
            case CORREO -> colaCorreo;
            case TICKET_TALLER -> colaTaller;
            case COMPROBANTE -> colaComprobante;
          };
      if (!esperada.equals(mensaje.getMessageProperties().getConsumerQueue()))
        throw new IllegalArgumentException("Tipo incompatible con la cola");
      for (int intento = 1; ; intento++) {
        try {
          service.recibir(comando);
          break;
        } catch (org.springframework.dao.TransientDataAccessException error) {
          if (intento >= 3) throw error;
          try {
            Thread.sleep(500L * intento);
          } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpido);
          }
        }
      }
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException error) {
      log.warn(
          "Comando rechazado a DLQ en {}: {}",
          mensaje.getMessageProperties().getConsumerQueue(),
          error.getClass().getSimpleName());
      canal.basicNack(entrega, false, false);
      return;
    }
    // ACK después del commit de la bandeja durable; SMTP se procesa por separado.
    canal.basicAck(entrega, false);
  }
}
