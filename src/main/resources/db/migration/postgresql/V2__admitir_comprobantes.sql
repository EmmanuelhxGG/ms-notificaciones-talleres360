-- Hibernate update no amplía automáticamente los CHECK de enums existentes.
-- La base previa se adopta como versión 1; una base nueva será creada por JPA.
DO $$
BEGIN
    IF to_regclass('public.notificaciones') IS NOT NULL THEN
        ALTER TABLE public.notificaciones
            DROP CONSTRAINT IF EXISTS notificaciones_tipo_check;
        ALTER TABLE public.notificaciones
            ADD CONSTRAINT notificaciones_tipo_check
            CHECK (tipo IN ('CORREO', 'TICKET_TALLER', 'COMPROBANTE'));
    END IF;
END $$;
