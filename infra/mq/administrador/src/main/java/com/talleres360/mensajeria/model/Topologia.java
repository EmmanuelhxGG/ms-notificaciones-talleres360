package com.talleres360.mensajeria.model;

import jakarta.validation.constraints.*;

public final class Topologia {
  private Topologia() {}

  public record Cola(@NotBlank @Pattern(regexp = "q\\.[a-zA-Z0-9._-]{1,90}") String nombre) {}

  public record Exchange(
      @NotBlank @Pattern(regexp = "cmd\\.[a-zA-Z0-9._-]{1,90}") String nombre,
      @NotBlank @Pattern(regexp = "direct|topic") String tipo) {}

  public record Enlace(
      @NotBlank @Pattern(regexp="cmd\\.[a-zA-Z0-9._-]{1,90}") String exchange,
      @NotBlank @Pattern(regexp="q\\.[a-zA-Z0-9._-]{1,90}") String cola,
      @NotBlank @Size(max = 150) @Pattern(regexp="[a-zA-Z0-9.*#_-]+") String clave) {}
}
