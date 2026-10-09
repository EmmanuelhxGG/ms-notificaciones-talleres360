package com.talleres360.notificaciones.repository;

import com.talleres360.notificaciones.model.Notificacion;
import com.talleres360.notificaciones.model.Notificacion.Estado;
import com.talleres360.notificaciones.model.TipoNotificacion;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface NotificacionRepository
    extends JpaRepository<Notificacion, String>, JpaSpecificationExecutor<Notificacion> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from Notificacion n where n.commandId = :id")
  Optional<Notificacion> bloquear(String id);

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
