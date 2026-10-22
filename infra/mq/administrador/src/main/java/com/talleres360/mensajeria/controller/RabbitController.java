package com.talleres360.mensajeria.controller;

import com.talleres360.mensajeria.model.Topologia;
import com.talleres360.mensajeria.service.RabbitService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messaging/rabbit")
@RequiredArgsConstructor
public class RabbitController {
  private final RabbitService service;

  @GetMapping("/queues")
  public Object colas() {
    return service.colas();
  }

  @GetMapping("/exchanges")
  public Object exchanges() {
    return service.exchanges();
  }

  @GetMapping("/nodes")
  public Object nodos() {
    return service.nodos();
  }

  @GetMapping("/bindings")
  public Object enlaces() {
    return service.enlaces();
  }

  @GetMapping("/policies")
  public Object politicas() {
    return service.politicas();
  }

  @PostMapping("/initialize")
  public void inicializar() {
    service.inicializar();
  }

  @PostMapping("/queues")
  public void crearCola(@Valid @RequestBody Topologia.Cola c) {
    service.cola(c.nombre(), Map.of("x-queue-type", "quorum", "x-quorum-initial-group-size", 2));
  }

  @PostMapping("/exchanges")
  public void crearExchange(@Valid @RequestBody Topologia.Exchange e) {
    service.exchange(e);
  }

  @PostMapping("/bindings")
  public void crearEnlace(@Valid @RequestBody Topologia.Enlace e) {
    service.enlace(e);
  }

  @DeleteMapping("/queues/{nombre}")
  public void eliminarCola(@PathVariable String nombre) {
    service.eliminarCola(nombre);
  }

  @DeleteMapping("/exchanges/{nombre}")
  public void eliminarExchange(@PathVariable String nombre) {
    service.eliminarExchange(nombre);
  }
}
