package com.talleres360.notificaciones;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.messaging.ConsumidorRabbit;
import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.service.NotificacionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

class ConsumidorRabbitTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final NotificacionService service = mock(NotificacionService.class);
  private final ConsumidorRabbit consumidor = new ConsumidorRabbit(json, service);

  @Test
  void interpretaJsonYDelegaLaPersistenciaAntesDeRetornar() throws Exception {
    var c =
        new ComandoNotificacion(
            UUID.randomUUID().toString(),
            1L,
            TipoNotificacion.TICKET_TALLER,
            null,
            "Orden recibida",
            "Preparar revisión del vehículo.",
            Instant.now());
    consumidor.consumir(new Message(json.writeValueAsBytes(c), new MessageProperties()));
    verify(service).recibir(c);
  }

  @Test
  void rechazaJsonDanadoYTamanosExcesivos() {
    assertThrows(
        AmqpRejectAndDontRequeueException.class,
        () -> consumidor.consumir(new Message(new byte[] {'{'}, new MessageProperties())));
    assertThrows(
        AmqpRejectAndDontRequeueException.class,
        () -> consumidor.consumir(new Message(new byte[65537], new MessageProperties())));
    verifyNoInteractions(service);
  }

  @Test
  void falloDeBaseDeDatosSePropagaParaQueElContenedorReintente() throws Exception {
    doThrow(new DataAccessResourceFailureException("Prueba")).when(service).recibir(any());
    var c =
        new ComandoNotificacion(
            UUID.randomUUID().toString(),
            1L,
            TipoNotificacion.TICKET_TALLER,
            null,
            "Orden recibida",
            "Preparar revisión.",
            Instant.now());
    assertThrows(
        DataAccessResourceFailureException.class,
        () -> consumidor.consumir(new Message(json.writeValueAsBytes(c), new MessageProperties())));
  }
}
