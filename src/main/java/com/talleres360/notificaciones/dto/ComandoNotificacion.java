package com.talleres360.notificaciones.dto;

import com.talleres360.notificaciones.model.TipoNotificacion;
import jakarta.validation.constraints.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ComandoNotificacion(
    @NotBlank
        @Pattern(
            regexp =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String commandId,
    @NotNull @Positive @JsonProperty("pedidoId") @JsonAlias("orderId") Long orderId,
    @NotNull TipoNotificacion tipo,
    @Email @Size(max = 150) @JsonProperty("email") @JsonAlias("destinatario") String destinatario,
    @NotBlank @Size(max = 180) @Pattern(regexp = "^[^\\r\\n]*$") String asunto,
    @NotBlank @Size(max = 32000) String mensaje,
    @NotNull Instant occurredAt,
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String comprobanteId) {}
