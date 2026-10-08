package com.talleres360.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class ConsumidorRabbit {
  private final ObjectMapper json;
  private final NotificacionService service;

  @RabbitListener(
      queues = {
        "${app.rabbit.email-queue:q.cmd.email}",
        "${app.rabbit.workshop-queue:q.cmd.workshop}"
      })
  public void consumir(Message mensaje) throws java.io.IOException {
    if (mensaje.getBody().length > 65536)
      throw new AmqpRejectAndDontRequeueException("Comando demasiado grande");
    try {
      service.recibir(json.readValue(mensaje.getBody(), ComandoNotificacion.class));
    } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
      throw new AmqpRejectAndDontRequeueException("Comando inválido; enviar a DLQ", error);
    }
  }
}
