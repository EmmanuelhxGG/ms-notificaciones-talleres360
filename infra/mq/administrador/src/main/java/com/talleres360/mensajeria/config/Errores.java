package com.talleres360.mensajeria.config;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errores {
  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<ProblemDetail> entradaInvalida(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Revisa los campos y el formato de la solicitud."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalido(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> noDisponible(Exception e) {
    return ResponseEntity.status(503)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "No se pudo realizar la operación de mensajería."));
  }
}
