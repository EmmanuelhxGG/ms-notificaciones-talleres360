package com.talleres360.notificaciones.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ClaveInterna extends OncePerRequestFilter {
  @Value("${app.internal-key}")
  private String clave;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String ruta = request.getRequestURI().substring(request.getContextPath().length());
    boolean publica =
        request.getMethod().equals("GET")
            && (ruta.equals("/actuator/health")
                || ruta.equals("/swagger-ui.html")
                || ruta.startsWith("/swagger-ui/")
                || ruta.equals("/v3/api-docs")
                || ruta.startsWith("/v3/api-docs/"));
    if (!publica) {
      String recibida = request.getHeader("X-Internal-Key");
      if (clave.isBlank()
          || recibida == null
          || !MessageDigest.isEqual(
              clave.getBytes(StandardCharsets.UTF_8), recibida.getBytes(StandardCharsets.UTF_8))) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response
            .getWriter()
            .write(
                "{\"title\":\"Acceso no autorizado\",\"status\":401,\"detail\":\"Credencial interna"
                    + " inválida\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
