-- Mantener los avisos históricos y asociar nuevos correos de entrega a su PDF.
ALTER TABLE IF EXISTS notificaciones
    ADD COLUMN IF NOT EXISTS comprobante_id VARCHAR(36);
