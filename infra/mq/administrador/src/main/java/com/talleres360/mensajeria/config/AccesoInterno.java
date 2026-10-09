package com.talleres360.mensajeria.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccesoInterno extends OncePerRequestFilter {
  @Value("${app.internal-key:}")
  private String clave;

  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
      throws ServletException, IOException {
    if (r.getRequestURI().equals("/actuator/health") && r.getMethod().equals("GET")) {
      c.doFilter(r, s);
      return;
    }
    String recibida = r.getHeader("X-Internal-Key");
    if (clave.isBlank()
        || recibida == null
        || !MessageDigest.isEqual(
            clave.getBytes(StandardCharsets.UTF_8), recibida.getBytes(StandardCharsets.UTF_8))) {
      s.sendError(401);
      return;
    }
    if (!"Admin".equals(r.getHeader("X-Actor-Role"))) {
      s.sendError(403);
      return;
    }
    c.doFilter(r, s);
  }
}
