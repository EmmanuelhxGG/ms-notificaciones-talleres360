package com.talleres360.notificaciones.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Falla al arrancar, sin mostrar secretos, si se habilita SMTP incompleto. */
@Component
public class ConfiguracionCorreo {
  @Value("${app.smtp.enabled:false}")
  private boolean habilitado;

  @Value("${spring.mail.host:}")
  private String servidor;

  @Value("${spring.mail.username:}")
  private String usuario;

  @Value("${spring.mail.password:}")
  private String clave;

  @Value("${app.smtp.from:}")
  private String remitente;

  @PostConstruct
  void validar() {
    if (!habilitado) return;
    if (servidor.isBlank() || usuario.isBlank() || clave.isBlank() || remitente.isBlank())
      throw new IllegalStateException("SMTP_ENABLED requiere servidor, usuario, credencial del proveedor y remitente");
  }
}
