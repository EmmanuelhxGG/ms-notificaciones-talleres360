-- Instalaciones existentes: respaldar y ejecutar antes de activar la nueva versión.
-- No elimina registros ni altera sus identidades.
ALTER TABLE notificaciones ALTER COLUMN order_id DROP NOT NULL;
