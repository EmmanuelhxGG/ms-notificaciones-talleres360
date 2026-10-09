package com.talleres360.notificaciones.controller;

import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.dto.NotificacionVista;
import com.talleres360.notificaciones.dto.Pagina;
import com.talleres360.notificaciones.service.NotificacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class NotificacionController {
  private final NotificacionService service;

  @PostMapping("/internal/commands")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void recibir(@Valid @RequestBody ComandoNotificacion input) {
    service.recibir(input);
  }

  @GetMapping("/api/notifications")
  public Pagina<NotificacionVista> listar(
      @RequestParam(required = false) @Positive Long orderId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestHeader("X-Actor-Role") String role,
      @RequestHeader(value = "X-Customer-Email", required = false) String correo) {
    if (!role.equals("Operador") && !role.equals("Cliente"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (role.equals("Cliente") && (correo == null || correo.isBlank()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return Pagina.desde(
        service
            .listar(
                orderId,
                role.equals("Operador"),
                role.equals("Cliente") ? correo : null,
                page,
                size)
            .map(NotificacionVista::desde));
  }

  @PostMapping("/api/notifications/{id}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void reintentar(@PathVariable String id, @RequestHeader("X-Actor-Role") String role) {
    if (!role.equals("Admin")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    service.reintentar(id);
  }
}
