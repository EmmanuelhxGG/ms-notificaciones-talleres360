package com.talleres360.notificaciones;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talleres360.notificaciones.dto.ComandoNotificacion;
import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.repository.NotificacionRepository;
import com.talleres360.notificaciones.service.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "INTERNAL_API_KEY=test-key",
      "app.worker.enabled=false",
      "app.smtp.enabled=true",
      "app.smtp.from=avisos@ejemplo.invalid",
      "management.health.mail.enabled=false"
    })
@AutoConfigureMockMvc
class NotificacionTest {
  @Autowired NotificacionService service;
  @Autowired ProcesadorNotificaciones procesador;
  @Autowired NotificacionRepository repository;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager transacciones;
  @MockitoBean JavaMailSender correo;

  @BeforeEach
  void limpiar() {
    repository.deleteAll();
    reset(correo);
  }

  private ComandoNotificacion comando(TipoNotificacion tipo) {
    return new ComandoNotificacion(
        UUID.randomUUID().toString(),
        1L,
        tipo,
        "cliente@ejemplo.invalid",
        "Orden actualizada",
        "Tu orden fue aceptada.",
        Instant.now());
  }

  @Test
  void duplicadoNoDuplicaCorreo() {
    var c = comando(TipoNotificacion.CORREO);
    service.recibir(c);
    service.recibir(c);
    procesador.procesar(c.commandId());
    procesador.procesar(c.commandId());
    assertEquals(1, repository.count());
    assertEquals(Estado.ENVIADA, repository.findById(c.commandId()).orElseThrow().getEstado());
    verify(correo, times(1)).send(any(SimpleMailMessage.class));
  }

  @Test
  void uuidConMayusculasNoCreaOtraNotificacion() {
    var c = comando(TipoNotificacion.CORREO);
    service.recibir(c);
    service.recibir(
        new ComandoNotificacion(
            c.commandId().toUpperCase(java.util.Locale.ROOT),
            c.orderId(),
            c.tipo(),
            c.destinatario(),
            c.asunto(),
            c.mensaje(),
            c.occurredAt()));
    assertEquals(1, repository.count());
  }

  @Test
  void ticketQuedaPersistidoSinEnviarCorreo() {
    var c = comando(TipoNotificacion.TICKET_TALLER);
    service.recibir(c);
    procesador.procesar(c.commandId());
    assertEquals(Estado.GENERADA, repository.findById(c.commandId()).orElseThrow().getEstado());
    verifyNoInteractions(correo);
  }

  @Test
  void falloSmtpPermiteReintentarSinDeclararEnviado() {
    var c = comando(TipoNotificacion.CORREO);
    service.recibir(c);
    doThrow(new MailSendException("Proveedor no disponible"))
        .when(correo)
        .send(any(SimpleMailMessage.class));
    procesador.procesar(c.commandId());
    var n = repository.findById(c.commandId()).orElseThrow();
    assertEquals(Estado.REINTENTO, n.getEstado());
    assertNull(n.getProcesadaEn());
    n.setEstado(Estado.FALLIDA);
    repository.save(n);
    service.reintentar(c.commandId());
    assertEquals(Estado.PENDIENTE, repository.findById(c.commandId()).orElseThrow().getEstado());
  }

  @Test
  void protegeLecturaYReintento() throws Exception {
    mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/notifications")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Operador"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/notifications/abc/retry")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Operador"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/notifications")
                .param("size", "1000")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Admin"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rechazaCorreoAusenteYConflictoDeIdentidad() {
    var c = comando(TipoNotificacion.CORREO);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recibir(
                new ComandoNotificacion(
                    c.commandId(),
                    1L,
                    TipoNotificacion.CORREO,
                    null,
                    "Estado",
                    "Mensaje",
                    Instant.now())));
    service.recibir(c);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recibir(
                new ComandoNotificacion(
                    c.commandId(),
                    2L,
                    c.tipo(),
                    c.destinatario(),
                    c.asunto(),
                    c.mensaje(),
                    c.occurredAt())));
  }

  @Test
  void smtpDeshabilitadoNoSimulaEnvioNiBloqueaTickets() {
    var c = comando(TipoNotificacion.CORREO);
    var t = comando(TipoNotificacion.TICKET_TALLER);
    service.recibir(c);
    service.recibir(t);
    var sinSmtp = new ProcesadorNotificaciones(repository, correo);
    ReflectionTestUtils.setField(sinSmtp, "smtpHabilitado", false);
    new TransactionTemplate(transacciones)
        .executeWithoutResult(estado -> sinSmtp.procesar(c.commandId()));
    assertEquals(Estado.PENDIENTE, repository.findById(c.commandId()).orElseThrow().getEstado());
    var pendientes =
        repository.pendientes(
            java.util.List.of(Estado.PENDIENTE),
            Instant.now(),
            false,
            TipoNotificacion.TICKET_TALLER,
            PageRequest.of(0, 50));
    assertEquals(java.util.List.of(t.commandId()), pendientes);
    verifyNoInteractions(correo);
  }

  @Test
  void agotaCincoIntentosYNoReenviaUnaNotificacionFallida() {
    var c = comando(TipoNotificacion.CORREO);
    service.recibir(c);
    doThrow(new MailSendException("Error de prueba"))
        .when(correo)
        .send(any(SimpleMailMessage.class));
    for (int i = 0; i < 5; i++) {
      var n = repository.findById(c.commandId()).orElseThrow();
      n.setProximoIntento(Instant.EPOCH);
      repository.saveAndFlush(n);
      procesador.procesar(c.commandId());
    }
    procesador.procesar(c.commandId());
    var n = repository.findById(c.commandId()).orElseThrow();
    assertEquals(Estado.FALLIDA, n.getEstado());
    assertEquals(5, n.getIntentos());
    assertNull(n.getProcesadaEn());
    verify(correo, times(5)).send(any(SimpleMailMessage.class));
  }

  @Test
  void ingestaHttpValidaYOperadorSoloLeeTickets() throws Exception {
    var c = comando(TipoNotificacion.CORREO);
    var t = comando(TipoNotificacion.TICKET_TALLER);
    mvc.perform(
            post("/internal/commands")
                .header("X-Internal-Key", "test-key")
                .contentType("application/json")
                .content(json.writeValueAsString(c)))
        .andExpect(status().isAccepted());
    service.recibir(t);
    mvc.perform(
            get("/api/notifications")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Operador"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].tipo").value("TICKET_TALLER"));
    mvc.perform(
            get("/api/notifications")
                .header("X-Internal-Key", "test-key")
                .header("X-Actor-Role", "Admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(
            post("/internal/commands")
                .header("X-Internal-Key", "test-key")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists());
  }

  @Test
  void rechazaUuidInvalidoYAsuntoConSaltoDeLinea() {
    var c = comando(TipoNotificacion.CORREO);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recibir(
                new ComandoNotificacion(
                    "------------------------------------",
                    1L,
                    c.tipo(),
                    c.destinatario(),
                    c.asunto(),
                    c.mensaje(),
                    c.occurredAt())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.recibir(
                new ComandoNotificacion(
                    c.commandId(),
                    1L,
                    c.tipo(),
                    c.destinatario(),
                    "Asunto\r\nBcc: otro@ejemplo.invalid",
                    c.mensaje(),
                    c.occurredAt())));
  }
}
