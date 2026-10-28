package com.talleres360.notificaciones.service;

import com.talleres360.notificaciones.model.MensajeFallido;
import com.talleres360.notificaciones.repository.MensajeFallidoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchivoCartasMuertas {
  private final MensajeFallidoRepository repository;

  @Transactional
  public String guardar(Message mensaje) {
    var p=mensaje.getMessageProperties();
    String contenido=new String(mensaje.getBody(),StandardCharsets.UTF_8);
    String id=UUID.nameUUIDFromBytes((p.getConsumerQueue()+":"+p.getMessageId()+":"+contenido)
        .getBytes(StandardCharsets.UTF_8)).toString();
    if (!repository.existsById(id)) {
      var fallo=new MensajeFallido();
      fallo.setId(id);
      fallo.setCola(p.getConsumerQueue());
      fallo.setMensajeId(p.getMessageId()==null ? null : p.getMessageId().substring(0,Math.min(160,p.getMessageId().length())));
      fallo.setContenido(contenido.substring(0,Math.min(65536,contenido.length())));
      String muerte=String.valueOf(p.getHeaders().getOrDefault("x-death","sin metadatos"));
      fallo.setMotivo("Mensaje rechazado por validación o agotamiento de reintentos; x-death="
          + muerte.substring(0,Math.min(350,muerte.length())));
      fallo.setRecibidoEn(Instant.now());
      repository.saveAndFlush(fallo);
    }
    return id;
  }
}
