-- Requiere que el usuario acepte devolucion con cargo antes de confirmar solicitud
ALTER TABLE bien_solicitudes
    ADD acepta_devolucion_con_cargo BIT NOT NULL DEFAULT 0;
