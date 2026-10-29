package com.talleres360.notificaciones.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "mensajes_fallidos")
@Getter
@Setter
@NoArgsConstructor
public class MensajeFallido {
  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "cola", nullable = false, length = 100)
  private String cola;

  @Column(name = "mensaje_id", length = 160)
  private String mensajeId;

  @Column(name = "contenido", nullable = false, length = 65536)
  private String contenido;

  @Column(name = "motivo", nullable = false, length = 500)
  private String motivo;

  @Column(name = "recibido_en", nullable = false)
  private Instant recibidoEn;
}
