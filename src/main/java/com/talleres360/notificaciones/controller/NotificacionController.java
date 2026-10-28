package com.talleres360.notificaciones.controller;

import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.dto.NotificacionVista;
import com.talleres360.notificaciones.dto.Pagina;
import com.talleres360.notificaciones.dto.SolicitudNotificaciones;
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

  @GetMapping("/api/notifications/solicitudes")
  public Pagina<SolicitudNotificaciones> solicitudes(
      @RequestParam(required=false) @Positive Long orderId,
      @RequestParam(defaultValue="0") @Min(0) int page,
      @RequestParam(defaultValue="10") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue="personal") String ambito,
      @RequestHeader("X-Actor-Role") String role,
      @RequestHeader(value="X-Customer-Email",required=false) String correo) {
    boolean operativo = validarAmbito(role, ambito, correo);
    return Pagina.desde(service.solicitudes(orderId, operativo, correo, page, size));
  }

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
      @RequestParam(defaultValue = "personal") String ambito,
      @RequestParam(defaultValue = "false") boolean generales,
      @RequestHeader("X-Actor-Role") String role,
      @RequestHeader(value = "X-Customer-Email", required = false) String correo) {
    boolean operativo = validarAmbito(role, ambito, correo);
    if (generales && orderId != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return Pagina.desde(
        service
            .listar(
                orderId,
                operativo,
                operativo ? null : correo,
                page,
                size,
                generales)
            .map(NotificacionVista::desde));
  }

  private boolean validarAmbito(String rol, String ambito, String correo) {
    if (!java.util.Set.of("Admin", "Operador", "Cliente").contains(rol))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (!java.util.Set.of("personal", "operativo").contains(ambito))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    if (ambito.equals("operativo")) {
      if (!rol.equals("Operador")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      return true;
    }
    if (correo == null || correo.isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return false;
  }

  @PostMapping("/api/notifications/{id}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void reintentar(@PathVariable String id, @RequestHeader("X-Actor-Role") String role) {
    if (!role.equals("Admin")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    service.reintentar(id);
  }
}
