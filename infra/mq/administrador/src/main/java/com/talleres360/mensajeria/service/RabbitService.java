package com.talleres360.mensajeria.service;

import com.talleres360.mensajeria.model.Topologia;
import com.talleres360.mensajeria.repository.RabbitRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitService {
  private final RabbitRepository repository;
  private static final Map<String, String> RUTAS =
      Map.of("email", "email.send", "workshop", "workshop.ticket", "invoice", "invoice.gen");

  public Object colas() {
    return repository.listar("queues/%2F");
  }

  public Object exchanges() {
    return repository.listar("exchanges/%2F");
  }

  public Object nodos() {
    return repository.listar("nodes");
  }

  public Object enlaces() {
    return repository.listar("bindings/%2F");
  }

  public Object politicas() {
    return repository.listar("policies/%2F");
  }

  public void cola(String nombre, Map<String, ?> argumentos) {
    validar(nombre, "q\\.[a-zA-Z0-9._-]{1,90}");
    repository.guardar(
        "queues", nombre, Map.of("durable", true, "auto_delete", false, "arguments", argumentos));
  }

  public void exchange(Topologia.Exchange e) {
    validar(e.nombre(), "cmd\\.[a-zA-Z0-9._-]{1,90}");
    if (!Set.of("direct", "topic").contains(e.tipo()))
      throw new IllegalArgumentException("Tipo inválido");
    repository.guardar(
        "exchanges",
        e.nombre(),
        Map.of(
            "type",
            e.tipo(),
            "durable",
            true,
            "auto_delete",
            false,
            "internal",
            false,
            "arguments",
            Map.of()));
  }

  public void enlace(Topologia.Enlace e) {
    validar(e.exchange(), "cmd\\.[a-zA-Z0-9._-]{1,90}");
    validar(e.cola(), "q\\.[a-zA-Z0-9._-]{1,90}");
    repository.vincular(e.exchange(), e.cola(), e.clave());
  }

  public void eliminarCola(String nombre) {
    validarDemo(nombre, "q.demo.");
    repository.eliminar("queues", nombre);
  }

  public void eliminarExchange(String nombre) {
    validarDemo(nombre, "cmd.demo.");
    repository.eliminar("exchanges", nombre);
  }

  public void inicializar() {
    if (!(nodos() instanceof List<?> nodos) || nodos.size() != 2)
      throw new IllegalArgumentException("Se requieren dos nodos RabbitMQ activos");
    exchange(new Topologia.Exchange("cmd.direct", "direct"));
    exchange(new Topologia.Exchange("cmd.topic", "topic"));
    exchange(new Topologia.Exchange("cmd.dead.dlx", "direct"));
    for (var entrada : RUTAS.entrySet()) {
      String cola = "q.cmd." + entrada.getKey();
      String ruta = entrada.getValue();
      asegurarCola(cola + ".dlq", null);
      // Política exacta: nunca se aplica a la DLQ ni exige borrar la cola para cambiar DLX.
      repository.guardar(
          "policies", "policy." + entrada.getKey() + ".dlx",
          Map.of(
              "pattern", "^q\\.cmd\\." + entrada.getKey() + "$",
              "apply-to", "queues",
              "priority", 0,
              "definition", Map.of(
                  "dead-letter-exchange", "cmd.dead.dlx",
                  "dead-letter-routing-key", ruta,
                  "dead-letter-strategy", "at-least-once",
                  "overflow", "reject-publish")));
      asegurarCola(cola, ruta);
      enlace(new Topologia.Enlace("cmd.direct", cola, ruta));
      enlace(new Topologia.Enlace("cmd.topic", cola,
          entrada.getKey().equals("invoice") ? "invoice.*" : entrada.getKey() + ".#"));
      if (entrada.getKey().equals("invoice"))
        repository.retirarEnlaceAnterior("cmd.topic", cola, "invoice.#");
      enlace(new Topologia.Enlace("cmd.dead.dlx", cola + ".dlq", ruta));
    }
  }

  private void asegurarCola(String nombre, String rutaMuerta) {
    var existente = repository.buscarCola(nombre);
    if (existente == null) {
      cola(nombre, Map.of("x-queue-type", "quorum", "x-quorum-initial-group-size", 2));
      return;
    }
    if (!Boolean.TRUE.equals(existente.get("durable"))
        || !Boolean.FALSE.equals(existente.get("auto_delete"))
        || !"quorum".equals(existente.get("type")))
      throw new IllegalStateException("La cola " + nombre + " requiere una migración explícita; no se borrará");
    if (rutaMuerta != null && existente.get("arguments") instanceof Map<?, ?> argumentos) {
      Object dlx = argumentos.get("x-dead-letter-exchange");
      Object ruta = argumentos.get("x-dead-letter-routing-key");
      if ((dlx != null && !"cmd.dead.dlx".equals(dlx))
          || (ruta != null && !rutaMuerta.equals(ruta)))
        throw new IllegalStateException("DLX anterior incompatible en " + nombre + "; no se borrará");
      if (dlx != null || ruta != null)
        log.warn("{} conserva argumentos DLX anteriores que prevalecen sobre la política; migrar drenando, nunca purgar", nombre);
    }
  }

  private void validar(String n, String patron) {
    if (n == null || !n.matches(patron)) throw new IllegalArgumentException("Nombre inválido");
  }

  private void validarDemo(String n, String prefijo) {
    if (n == null || !n.startsWith(prefijo) || !n.matches("[a-zA-Z0-9._-]{1,100}"))
      throw new IllegalArgumentException(
          "Solo se eliminan recursos de demostración; los recursos de negocio están protegidos");
  }
}
