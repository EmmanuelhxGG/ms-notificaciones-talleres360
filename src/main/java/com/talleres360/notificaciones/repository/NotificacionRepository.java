package com.talleres360.notificaciones.repository;

import com.talleres360.notificaciones.model.Notificacion;
import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.model.TipoNotificacion;
import com.talleres360.notificaciones.dto.SolicitudNotificaciones;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;

public interface NotificacionRepository
    extends JpaRepository<Notificacion, String>, JpaSpecificationExecutor<Notificacion> {
  @Query(value="""
      select new com.talleres360.notificaciones.dto.SolicitudNotificaciones(
          n.orderId, count(n), max(n.occurredAt))
      from Notificacion n
      where n.tipo in :tipos
        and (:correo is null or lower(n.destinatario) = :correo)
        and (:orderId is null or n.orderId = :orderId)
      group by n.orderId
      order by max(n.occurredAt) desc, n.orderId desc
      """, countQuery="""
      select count(distinct coalesce(n.orderId, -1)) from Notificacion n
      where n.tipo in :tipos
        and (:correo is null or lower(n.destinatario) = :correo)
        and (:orderId is null or n.orderId = :orderId)
      """)
  Page<SolicitudNotificaciones> solicitudes(
      Collection<TipoNotificacion> tipos, String correo, Long orderId, Pageable pagina);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from Notificacion n where n.commandId = :id")
  Optional<Notificacion> bloquear(String id);

  @Query("select n from Notificacion n where n.estado = :estado and coalesce(n.falloPublicado, false) = false order by n.occurredAt")
  List<Notificacion> fallosPendientes(Estado estado, Pageable limite);

  @org.springframework.transaction.annotation.Transactional
  @Modifying
  @Query("update Notificacion n set n.falloPublicado = true where n.commandId = :id and n.estado = :estado")
  int confirmarFallo(String id, Estado estado);

  boolean existsByOrderIdAndTipoAndEstadoNotAndOccurredAtBefore(
      Long orderId, TipoNotificacion tipo, Estado estado, Instant occurredAt);

  @Query(
      "select n.commandId from Notificacion n where n.estado in :estados and n.proximoIntento <="
          + " :ahora and (:incluirCorreos = true or n.tipo <> :tipoCorreo) order by"
          + " n.proximoIntento, n.commandId")
  List<String> pendientes(
      Collection<Estado> estados,
      Instant ahora,
      boolean incluirCorreos,
      TipoNotificacion tipoCorreo,
      Pageable limite);
}
