package com.talleres360.notificaciones.service;

import com.rabbitmq.client.Channel;
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
public class RegistroCartasMuertas {
  private final ArchivoCartasMuertas archivo;
  private final RetryTemplate reintentosRabbit;

  @RabbitListener(queues="#{@topologiaRabbit.colasMuertas}",
      autoStartup="${app.rabbit.dlq-auto-startup:true}", ackMode="MANUAL")
  public void registrar(Message mensaje, Channel canal) throws IOException {
    String id;
    try {
      id=reintentosRabbit.execute(contexto -> archivo.guardar(mensaje));
    } catch (RuntimeException error) {
      // No borrar una carta muerta cuando su archivo de BD está caído.
      log.warn("Archivo DLQ pendiente: cola={}, motivo={}",
          mensaje.getMessageProperties().getConsumerQueue(),error.getClass().getSimpleName());
      canal.basicNack(mensaje.getMessageProperties().getDeliveryTag(),false,true);
      return;
    }
    canal.basicAck(mensaje.getMessageProperties().getDeliveryTag(),false);
    log.warn("Carta muerta archivada y confirmada: id={}, cola={}",
        id,mensaje.getMessageProperties().getConsumerQueue());
  }
}
