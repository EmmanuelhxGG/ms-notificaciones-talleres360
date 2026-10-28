package com.talleres360.notificaciones.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.TransactionException;

@Configuration(value="configuracionReintentosRabbit", proxyBeanMethods=false)
public class ReintentosRabbit {
  @Bean
  public RetryTemplate reintentosRabbit(
      @Value("${app.rabbit.intentos:3}") int intentos,
      @Value("${app.rabbit.espera-ms:1000}") long espera) {
    if (intentos<1 || intentos>10 || espera<1 || espera>10000)
      throw new IllegalArgumentException("Reintentos RabbitMQ fuera de rango");
    var plantilla=new RetryTemplate();
    plantilla.setRetryPolicy(new SimpleRetryPolicy(intentos,
        Map.of(DataAccessException.class,true,TransactionException.class,true),true));
    var pausas=new ExponentialBackOffPolicy();
    pausas.setInitialInterval(espera);
    pausas.setMultiplier(2);
    pausas.setMaxInterval(10000);
    plantilla.setBackOffPolicy(pausas);
    return plantilla;
  }
}
