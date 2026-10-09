package com.talleres360.mensajeria.repository;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

@Repository
public class RabbitRepository {
  private final RestClient cliente;
  private final String base;

  public RabbitRepository(
      @Value("${app.rabbit-url}") String url,
      @Value("${app.rabbit-username}") String usuario,
      @Value("${app.rabbit-password}") String clave) {
    base = url.replaceAll("/+$", "");
    var tiempos = new SimpleClientHttpRequestFactory();
    tiempos.setConnectTimeout(3000);
    tiempos.setReadTimeout(5000);
    cliente =
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(tiempos)
            .defaultHeaders(h -> h.setBasicAuth(usuario, clave))
            .build();
  }

  public Object listar(String recurso) {
    return cliente.get().uri(ruta(recurso)).retrieve().body(Object.class);
  }

  public void guardar(String tipo, String nombre, Map<String, ?> cuerpo) {
    cliente.put().uri(ruta(tipo + "/%2F/" + nombre)).body(cuerpo).retrieve().toBodilessEntity();
  }

  public void vincular(String exchange, String cola, String clave) {
    cliente
        .post()
        .uri(ruta("bindings/%2F/e/" + exchange + "/q/" + cola))
        .body(Map.of("routing_key", clave, "arguments", Map.of()))
        .retrieve()
        .toBodilessEntity();
  }

  public void eliminar(String tipo, String nombre) {
    String condiciones = "?if-unused=true";
    if (tipo.equals("queues")) {
      var cola = (Map<?, ?>) listar("queues/%2F/" + nombre);
      if (!(cola.get("consumers") instanceof Number consumidores)
          || consumidores.intValue() != 0)
        throw new IllegalArgumentException("La cola tiene consumidores o no se pudo verificar su uso");
      // RabbitMQ no admite if-unused al borrar colas quorum. Solo se borran colas demo
      // vacías, sin consumidores observados; if-empty se comprueba de forma atómica en el broker.
      condiciones =
          "?if-empty=true" + ("quorum".equals(cola.get("type")) ? "" : "&if-unused=true");
    }
    cliente
        .delete()
        .uri(ruta(tipo + "/%2F/" + nombre + condiciones))
        .retrieve()
        .toBodilessEntity();
  }

  private java.net.URI ruta(String recurso) {
    // URI absoluta evita codificar dos veces el vhost / representado como %2F.
    return java.net.URI.create(base + "/api/" + recurso);
  }
}
