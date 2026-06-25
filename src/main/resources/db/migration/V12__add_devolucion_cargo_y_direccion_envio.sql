-- Requiere que el usuario acepte devolucion con cargo antes de confirmar solicitud
ALTER TABLE bien_solicitudes
    ADD acepta_devolucion_con_cargo BIT NOT NULL DEFAULT 0;

-- Permite al admin informar la direccion de envio para inspeccion del bien
ALTER TABLE productos_detalle
    ADD direccion_envio_inspeccion NVARCHAR(500) NULL;
