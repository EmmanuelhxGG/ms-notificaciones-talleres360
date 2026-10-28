package com.talleres360.mensajeria.repository;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

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

  public Map<?, ?> buscarCola(String nombre) {
    try {
      return cliente.get().uri(ruta("queues/%2F/" + nombre)).retrieve().body(Map.class);
    } catch (HttpClientErrorException.NotFound ausente) {
      return null;
    }
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
      if (!(cola.get("messages") instanceof Number mensajes) || mensajes.longValue() != 0)
        throw new IllegalArgumentException("La cola contiene mensajes o no se pudo verificar su estado");
      // Quorum no admite if-empty ni if-unused. La comprobación previa NO es atómica:
      // esta ruta está limitada por el servicio a colas q.demo., nunca a las de negocio.
      condiciones =
          "quorum".equals(cola.get("type")) ? "" : "?if-empty=true&if-unused=true";
    }
    cliente
        .delete()
        .uri(ruta(tipo + "/%2F/" + nombre + condiciones))
        .retrieve()
        .toBodilessEntity();
  }

  public void retirarEnlaceAnterior(String exchange, String cola, String clave) {
    Object respuesta = listar("bindings/%2F/e/" + exchange + "/q/" + cola);
    if (!(respuesta instanceof java.util.List<?> enlaces))
      throw new IllegalStateException("No se pudieron comprobar los enlaces anteriores");
    for (var enlace : enlaces) {
      if (enlace instanceof Map<?, ?> datos && clave.equals(datos.get("routing_key"))) {
        String identificador = java.net.URLEncoder.encode(
            String.valueOf(datos.get("properties_key")), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        cliente.delete().uri(ruta("bindings/%2F/e/" + exchange + "/q/" + cola + "/" + identificador))
            .retrieve().toBodilessEntity();
      }
    }
  }

  private java.net.URI ruta(String recurso) {
    // URI absoluta evita codificar dos veces el vhost / representado como %2F.
    return java.net.URI.create(base + "/api/" + recurso);
  }
}
