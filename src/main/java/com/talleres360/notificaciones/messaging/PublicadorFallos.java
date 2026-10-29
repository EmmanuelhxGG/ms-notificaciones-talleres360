package com.talleres360.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.notificaciones.config.TopologiaRabbit;
import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.repository.NotificacionRepository;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Los fallos de envío posteriores al ACK también quedan visibles en la DLQ. */
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.rabbit.enabled",havingValue="true")
public class PublicadorFallos {
  private final NotificacionRepository repository;
  private final RabbitTemplate rabbit;
  private final TopologiaRabbit topologia;
  private final ObjectMapper json;

  @Scheduled(fixedDelayString="${NOTIFICATIONS_POLL_MS:3000}")
  public void publicar() {
    for (var n : repository.fallosPendientes(Estado.FALLIDA, PageRequest.of(0,50))) {
      try {
        var cuerpo = json.createObjectNode();
        cuerpo.put("commandId",n.getCommandId());
        if (n.getOrderId()!=null) cuerpo.put("pedidoId",n.getOrderId());
        cuerpo.put("tipo",n.getTipo().name()); cuerpo.put("email",n.getDestinatario());
        cuerpo.put("asunto",n.getAsunto()); cuerpo.put("mensaje",n.getMensaje());
        cuerpo.put("occurredAt",n.getOccurredAt().toString());
        var p=new MessageProperties();p.setMessageId(n.getCommandId());
        p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);p.setContentType("application/json");
        p.setHeader("x-error-origen","envio-notificacion");
        p.setHeader("x-error-motivo",n.getUltimoError());
        var confirmacion=new CorrelationData(n.getCommandId());
        rabbit.send(topologia.getMuertas(),topologia.destino(n.getTipo().name()).ruta(),
            new Message(json.writeValueAsBytes(cuerpo),p),confirmacion);
        if (!confirmacion.getFuture().get(10,TimeUnit.SECONDS).isAck() || confirmacion.getReturned()!=null)
          throw new IllegalStateException("DLQ no confirmada");
        repository.confirmarFallo(n.getCommandId(),Estado.FALLIDA);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();return;
      } catch (Exception error) {
        log.warn("Publicación DLQ pendiente para {} ({})",n.getCommandId(),error.getClass().getSimpleName());
        return;
      }
    }
  }
}
