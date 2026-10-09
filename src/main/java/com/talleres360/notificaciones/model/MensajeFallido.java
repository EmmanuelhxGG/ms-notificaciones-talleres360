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
  @Column(length = 36)
  private String id;

  @Column(nullable = false, length = 100)
  private String cola;

  @Column(length = 160)
  private String mensajeId;

  @Column(nullable = false, length = 65536)
  private String contenido;

  @Column(nullable = false, length = 500)
  private String motivo;

  @Column(nullable = false)
  private Instant recibidoEn;
}
