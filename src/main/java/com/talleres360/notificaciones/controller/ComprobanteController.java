package com.talleres360.notificaciones.controller;

import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ComprobanteController {
  private final NotificacionRepository repository;

  @GetMapping("/api/notifications/{id}/receipt")
  public ResponseEntity<byte[]> descargar(
      @PathVariable String id,
      @RequestHeader("X-Actor-Role") String rol,
      @RequestHeader(value = "X-Customer-Email", required = false) String correo) {
    var n =
        repository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!(rol.equals("Admin")
        || rol.equals("Operador")
        || (rol.equals("Cliente")
            && correo != null
            && correo.equalsIgnoreCase(n.getDestinatario()))))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (n.getTipo() != TipoNotificacion.COMPROBANTE
        || n.getEstado() != com.talleres360.notificaciones.model.Notificacion.Estado.GENERADA)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=comprobante-" + n.getOrderId() + ".txt")
        .cacheControl(CacheControl.noStore())
        .body(n.getMensaje().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
