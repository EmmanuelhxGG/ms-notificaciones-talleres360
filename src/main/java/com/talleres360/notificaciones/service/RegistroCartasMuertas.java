package com.talleres360.notificaciones.service;

import com.rabbitmq.client.Channel;
import com.talleres360.notificaciones.model.MensajeFallido;
import com.talleres360.notificaciones.repository.MensajeFallidoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RegistroCartasMuertas {
  private final MensajeFallidoRepository repository;

  @RabbitListener(
      queues = {"q.cmd.email.dlq", "q.cmd.workshop.dlq", "q.cmd.invoice.dlq"},
      autoStartup = "${app.rabbit.dlq-auto-startup:false}",
      ackMode = "MANUAL")
  public void registrar(Message mensaje, Channel canal) throws java.io.IOException {
    var propiedades = mensaje.getMessageProperties();
    String contenido = new String(mensaje.getBody(), StandardCharsets.UTF_8);
    String id =
        UUID.nameUUIDFromBytes(
                (propiedades.getConsumerQueue()
                        + ":"
                        + propiedades.getMessageId()
                        + ":"
                        + contenido)
                    .getBytes(StandardCharsets.UTF_8))
            .toString();
    if (!repository.existsById(id)) {
      var fallo = new MensajeFallido();
      fallo.setId(id);
      fallo.setCola(propiedades.getConsumerQueue());
      fallo.setMensajeId(propiedades.getMessageId());
      fallo.setContenido(contenido.substring(0, Math.min(contenido.length(), 65536)));
      fallo.setMotivo("Mensaje rechazado por validación o procesamiento");
      fallo.setRecibidoEn(Instant.now());
      repository.saveAndFlush(fallo);
      log.warn("Carta muerta registrada: {} en {}", id, fallo.getCola());
    }
    canal.basicAck(propiedades.getDeliveryTag(), false);
  }
}
