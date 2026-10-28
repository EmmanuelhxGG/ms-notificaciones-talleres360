package com.talleres360.notificaciones.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Contrato central de nombres. Los tres repositorios mantienen el mismo contrato. */
@Configuration("topologiaRabbit")
@ConfigurationProperties(prefix = "app.rabbit.topologia")
@Validated
@Getter
@Setter
public class TopologiaRabbit {
  @NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]{1,90}")
  private String directo = "cmd.direct";
  @NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]{1,90}")
  private String tematico = "cmd.topic";
  @NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]{1,90}")
  private String muertas = "cmd.dead.dlx";
  @Valid @NotEmpty
  private List<Destino> destinos = new ArrayList<>(List.of(
      new Destino("CORREO", "q.cmd.email", "email.send", "email.#"),
      new Destino("TICKET_TALLER", "q.cmd.workshop", "workshop.ticket", "workshop.#"),
      new Destino("COMPROBANTE", "q.cmd.invoice", "invoice.gen", "invoice.*")));

  public record Destino(
      @NotBlank String tipo,
      @NotBlank @Pattern(regexp="q\\.[a-zA-Z0-9._-]{1,90}") String cola,
      @NotBlank @Size(max=150) String ruta,
      @NotBlank @Size(max=150) String patron) {
    public String colaMuerta() { return cola + ".dlq"; }
  }

  public Destino destino(String tipo) {
    return destinos.stream().filter(d -> d.tipo().equals(tipo)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Tipo de tarea desconocido"));
  }
  public String[] getColas() { return destinos.stream().map(Destino::cola).toArray(String[]::new); }
  public String[] getColasMuertas() { return destinos.stream().map(Destino::colaMuerta).toArray(String[]::new); }
  public Map<String,Object> argumentos(Destino d, boolean dlq) {
    var a = new LinkedHashMap<String,Object>();
    a.put("x-queue-type", "quorum");
    a.put("x-quorum-initial-group-size", 2);
    if (!dlq) {
      a.put("x-dead-letter-exchange", muertas);
      a.put("x-dead-letter-routing-key", d.ruta());
      a.put("x-dead-letter-strategy", "at-least-once");
      a.put("x-overflow", "reject-publish");
    }
    return a;
  }

  @PostConstruct
  void validarContrato() {
    if (destinos.size()!=3 || !new HashSet<>(destinos.stream().map(Destino::tipo).toList())
        .equals(Set.of("CORREO","TICKET_TALLER","COMPROBANTE"))
        || new HashSet<>(List.of(directo,tematico,muertas)).size()!=3
        || new HashSet<>(Arrays.asList(getColas())).size()!=3
        || new HashSet<>(destinos.stream().map(Destino::ruta).toList()).size()!=3)
      throw new IllegalArgumentException("Topología RabbitMQ incompleta o duplicada");
  }
}
