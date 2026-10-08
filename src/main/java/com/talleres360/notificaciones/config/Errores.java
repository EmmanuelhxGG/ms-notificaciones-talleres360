package com.talleres360.notificaciones.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errores {
  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  ProblemDetail datosInvalidos(Exception error) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Revisa los datos y el rango de fechas ingresado.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflicto(DataIntegrityViolationException error) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "El registro ya existe o está siendo procesado. Inténtalo nuevamente.");
  }
}
