package com.talleres360.notificaciones.service;

import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.repository.NotificacionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcesadorNotificaciones {
  private final NotificacionRepository repository;
  private final JavaMailSender correo;

  @Value("${app.smtp.enabled:false}")
  private boolean smtpHabilitado;

  @Value("${app.smtp.from:}")
  private String remitente;

  @Transactional
  public void procesar(String id) {
    var n = repository.bloquear(id).orElse(null);
    if (n == null
        || (n.getEstado() != Estado.PENDIENTE && n.getEstado() != Estado.REINTENTO)
        || n.getProximoIntento().isAfter(Instant.now())) return;
    // Sin proveedor configurado el correo sigue pendiente, nunca se simula un envío.
    if (n.getTipo() == TipoNotificacion.CORREO && !smtpHabilitado) return;
    n.setIntentos(n.getIntentos() + 1);
    try {
      if (n.getTipo() == TipoNotificacion.CORREO) {
        if (remitente.isBlank()) throw new IllegalStateException("Falta configurar el remitente");
        var mensaje = correo.createMimeMessage();
        var contenido = new MimeMessageHelper(mensaje, false, "UTF-8");
        contenido.setFrom(remitente);
        contenido.setTo(n.getDestinatario());
        contenido.setSubject(n.getAsunto());
        contenido.setText(n.getMensaje(), false);
        correo.send(mensaje);
        n.setEstado(Estado.ENVIADA);
      } else {
        // Comandos históricos de comprobante se conservan como texto, sin crear PDF.
        n.setEstado(Estado.GENERADA);
      }
      n.setProcesadaEn(Instant.now());
      n.setUltimoError(null);
    } catch (MailException | MessagingException | IllegalStateException error) {
      n.setEstado(n.getIntentos() >= 5 ? Estado.FALLIDA : Estado.REINTENTO);
      n.setProximoIntento(
          Instant.now().plusSeconds(Math.min(3600, 30L * (1L << Math.min(n.getIntentos(), 7)))));
      n.setUltimoError("No se pudo enviar la notificación. Revisa la configuración del proveedor.");
    }
  }
}
