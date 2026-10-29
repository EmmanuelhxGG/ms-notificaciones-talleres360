package com.talleres360.notificaciones.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.springframework.data.domain.Persistable;

@Entity
@Table(
    name = "notificaciones",
    indexes = @Index(name = "idx_noti_pendiente", columnList = "estado,proximo_intento"))
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
  @Column(name = "comando_id", length = 36)
  private String commandId;

  @Column(name = "solicitud_id", nullable = true)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo", nullable = false, length = 30)
  private TipoNotificacion tipo;

  @Column(name = "destinatario", length = 150)
  private String destinatario;

  @Column(name = "asunto", nullable = false, length = 180)
  private String asunto;

  @Column(name = "mensaje", nullable = false, length = 32000)
  private String mensaje;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado", nullable = false, length = 20)
  private Estado estado = Estado.PENDIENTE;

  @Column(name = "ocurrido_en", nullable = false)
  private Instant occurredAt;

  @Column(name = "proximo_intento", nullable = false)
  private Instant proximoIntento = Instant.now();

  @Column(name = "procesada_en") private Instant procesadaEn;
  @Column(name = "intentos") private int intentos;

  @Column(name = "ultimo_error", length = 300)
  private String ultimoError;

  // Outbox de fallos definitivos: se confirma solo después de persistir en RabbitMQ.
  @Column(name = "fallo_publicado") private Boolean falloPublicado = false;

  @JsonIgnore
  @Column(name = "comprobante_pdf")
  private byte[] comprobantePdf;

  @Column(name = "comprobante_id", length = 36)
  private String comprobanteId;

  public enum Estado {
    PENDIENTE,
    REINTENTO,
    ENVIADA,
    GENERADA,
    FALLIDA
  }
}
