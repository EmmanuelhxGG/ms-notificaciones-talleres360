package com.talleres360.notificaciones.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.domain.Persistable;

@Entity
@Table(
    name = "notificaciones",
    indexes = @Index(name = "idx_noti_pendiente", columnList = "estado,proximoIntento"))
@Getter
@Setter
@NoArgsConstructor
public class Notificacion implements Persistable<String> {
  @Transient private boolean nuevo = true;

  @Override
  @JsonIgnore
  public String getId() {
    return commandId;
  }

  @Override
  @JsonIgnore
  public boolean isNew() {
    return nuevo;
  }

  @PostLoad
  @PostPersist
  private void marcarExistente() {
    nuevo = false;
  }

  @Id
  @Column(length = 36)
  private String commandId;

  @Column(nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TipoNotificacion tipo;

  @Column(length = 150)
  private String destinatario;

  @Column(nullable = false, length = 180)
  private String asunto;

  @Column(nullable = false, length = 32000)
  private String mensaje;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Estado estado = Estado.PENDIENTE;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private Instant proximoIntento = Instant.now();

  private Instant procesadaEn;
  private int intentos;

  @Column(length = 300)
  private String ultimoError;

  @JsonIgnore
  @Column(name = "comprobante_pdf")
  private byte[] comprobantePdf;

  @Column(length = 36)
  private String comprobanteId;

  public enum Estado {
    PENDIENTE,
    REINTENTO,
    ENVIADA,
    GENERADA,
    FALLIDA
  }
}
