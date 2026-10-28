package com.talleres360.notificaciones.config;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import lombok.RequiredArgsConstructor;

/** Declaración idempotente: conserva exactamente los argumentos de las colas de AWS. */
@Configuration(proxyBeanMethods=false)
@RequiredArgsConstructor
@ConditionalOnProperty(name="app.rabbit.enabled", havingValue="true")
public class DeclaracionTopologiaRabbit {
  private final TopologiaRabbit topologia;
  @Bean public DirectExchange exchangeComandos() { return new DirectExchange(topologia.getDirecto(), true, false); }
  @Bean public TopicExchange exchangeTematico() { return new TopicExchange(topologia.getTematico(), true, false); }
  @Bean public DirectExchange exchangeCartasMuertas() { return new DirectExchange(topologia.getMuertas(), true, false); }
  private Queue cola(String tipo, boolean dlq) {
    var d=topologia.destino(tipo);
    return new Queue(dlq ? d.colaMuerta() : d.cola(), true, false, false, topologia.argumentos(d,dlq));
  }
  @Bean public Queue colaCorreo() { return cola("CORREO",false); }
  @Bean public Queue dlqCorreo() { return cola("CORREO",true); }
  @Bean public Binding enlaceCorreoDirecto(@Qualifier("colaCorreo") Queue q, @Qualifier("exchangeComandos") DirectExchange exchangeComandos) {
    return BindingBuilder.bind(q).to(exchangeComandos).with(topologia.destino("CORREO").ruta());
  }
  @Bean public Binding enlaceCorreoTematico(@Qualifier("colaCorreo") Queue q, TopicExchange exchangeTematico) {
    return BindingBuilder.bind(q).to(exchangeTematico).with(topologia.destino("CORREO").patron());
  }
  @Bean public Binding enlaceCorreoDlq(@Qualifier("dlqCorreo") Queue q, @Qualifier("exchangeCartasMuertas") DirectExchange x) {
    return BindingBuilder.bind(q).to(x).with(topologia.destino("CORREO").ruta());
  }
  @Bean public Queue colaTaller() { return cola("TICKET_TALLER",false); }
  @Bean public Queue dlqTaller() { return cola("TICKET_TALLER",true); }
  @Bean public Binding enlaceTallerDirecto(@Qualifier("colaTaller") Queue q, @Qualifier("exchangeComandos") DirectExchange exchangeComandos) {
    return BindingBuilder.bind(q).to(exchangeComandos).with(topologia.destino("TICKET_TALLER").ruta());
  }
  @Bean public Binding enlaceTallerTematico(@Qualifier("colaTaller") Queue q, TopicExchange exchangeTematico) {
    return BindingBuilder.bind(q).to(exchangeTematico).with(topologia.destino("TICKET_TALLER").patron());
  }
  @Bean public Binding enlaceTallerDlq(@Qualifier("dlqTaller") Queue q, @Qualifier("exchangeCartasMuertas") DirectExchange x) {
    return BindingBuilder.bind(q).to(x).with(topologia.destino("TICKET_TALLER").ruta());
  }
  @Bean public Queue colaComprobante() { return cola("COMPROBANTE",false); }
  @Bean public Queue dlqComprobante() { return cola("COMPROBANTE",true); }
  @Bean public Binding enlaceComprobanteDirecto(@Qualifier("colaComprobante") Queue q, @Qualifier("exchangeComandos") DirectExchange exchangeComandos) {
    return BindingBuilder.bind(q).to(exchangeComandos).with(topologia.destino("COMPROBANTE").ruta());
  }
  @Bean public Binding enlaceComprobanteTematico(@Qualifier("colaComprobante") Queue q, TopicExchange exchangeTematico) {
    return BindingBuilder.bind(q).to(exchangeTematico).with(topologia.destino("COMPROBANTE").patron());
  }
  @Bean public Binding enlaceComprobanteDlq(@Qualifier("dlqComprobante") Queue q, @Qualifier("exchangeCartasMuertas") DirectExchange x) {
    return BindingBuilder.bind(q).to(x).with(topologia.destino("COMPROBANTE").ruta());
  }
}
