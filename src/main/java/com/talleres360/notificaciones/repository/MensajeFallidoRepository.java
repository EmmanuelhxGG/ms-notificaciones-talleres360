package com.talleres360.notificaciones.repository;

import com.talleres360.notificaciones.model.MensajeFallido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeFallidoRepository extends JpaRepository<MensajeFallido, String> {}
