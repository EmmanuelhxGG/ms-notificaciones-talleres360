package com.talleres360.notificaciones.dto;

import jakarta.validation.constraints.*;

/** JSON sencillo publicado desde Management: no inventa una orden para el mensaje. */
public record CorreoManual(
    @NotBlank @Email @Size(max=150) String email,
    @NotBlank @Size(max=180) @Pattern(regexp="^[^\\r\\n]*$") String asunto,
    @NotBlank @Size(max=32000) String mensaje) {}
