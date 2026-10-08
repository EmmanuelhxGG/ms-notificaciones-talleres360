package com.talleres360.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Aplicacion {
  public static void main(String[] args) {
    SpringApplication.run(Aplicacion.class, args);
  }
}
