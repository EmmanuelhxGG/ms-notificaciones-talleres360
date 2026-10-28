package com.talleres360.notificaciones.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.talleres360.notificaciones.config.TopologiaRabbit;
import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.service.NotificacionService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.rabbit.enabled", havingValue="true")
public class ConsumidorRabbit {
  private final ObjectMapper json;
  private final NotificacionService service;
  private final TopologiaRabbit topologia;
  private final RetryTemplate reintentosRabbit;

  @RabbitListener(queues="#{@topologiaRabbit.colas}", ackMode="MANUAL")
  public void consumir(Message mensaje, Channel canal) throws IOException {
    var p = mensaje.getMessageProperties();
    long entrega = p.getDeliveryTag();
    ComandoNotificacion comando;
    try {
      if (mensaje.getBody().length>65536) throw new IllegalArgumentException("Comando demasiado grande");
      comando=json.readValue(mensaje.getBody(), ComandoNotificacion.class);
      if (comando==null || comando.tipo()==null) throw new IllegalArgumentException("Comando sin tipo");
      if (!topologia.destino(comando.tipo().name()).cola().equals(p.getConsumerQueue()))
        throw new IllegalArgumentException("Tipo incompatible con la cola");
    } catch (IOException | IllegalArgumentException error) {
      log.warn("NACK a DLQ: cola={}, motivo={}",p.getConsumerQueue(),error.getClass().getSimpleName());
      canal.basicNack(entrega,false,false);
      return;
    }
    try {
      reintentosRabbit.execute(contexto -> {
        // Cada intento usa el proxy: el commit de PostgreSQL termina antes del ACK.
        service.recibir(comando);
        return null;
      });
    } catch (RuntimeException error) {
      // Datos inválidos no se reintentan; errores de BD tienen reintentos acotados.
      log.warn("NACK a DLQ tras procesamiento: cola={}, id={}, motivo={}",
          p.getConsumerQueue(),p.getMessageId(),error.getClass().getSimpleName());
      canal.basicNack(entrega,false,false);
      return;
    }
    canal.basicAck(entrega,false);
    log.debug("ACK después de persistir: cola={}, id={}",p.getConsumerQueue(),p.getMessageId());
  }
}
