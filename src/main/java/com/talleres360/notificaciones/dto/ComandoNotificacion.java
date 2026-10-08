package com.talleres360.notificaciones.dto;

import com.talleres360.notificaciones.model.TipoNotificacion;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record ComandoNotificacion(
    @NotBlank
        @Pattern(
            regexp =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String commandId,
    @NotNull @Positive Long orderId,
    @NotNull TipoNotificacion tipo,
    @Email @Size(max = 150) String destinatario,
    @NotBlank @Size(max = 180) @Pattern(regexp = "^[^\\r\\n]*$") String asunto,
    @NotBlank @Size(max = 4000) String mensaje,
    @NotNull Instant occurredAt) {}
