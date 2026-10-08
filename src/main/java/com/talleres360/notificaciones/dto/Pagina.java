package com.talleres360.notificaciones.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Contrato de paginación propio, independiente de la serialización interna de Spring. */
public record Pagina<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
  public static <T> Pagina<T> desde(Page<T> pagina) {
    return new Pagina<>(
        pagina.getContent(),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
