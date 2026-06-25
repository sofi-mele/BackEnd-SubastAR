package com.subastar.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void createMissingTables() {
        try {
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='cuentas_cobro' AND xtype='U')
                CREATE TABLE cuentas_cobro (
                    id           INT IDENTITY(1,1) PRIMARY KEY,
                    cliente_id   INT           NOT NULL,
                    nombre_banco NVARCHAR(200) NOT NULL,
                    cbu_iban     NVARCHAR(100) NOT NULL,
                    pais         NVARCHAR(100) NOT NULL,
                    moneda       NVARCHAR(10)  NOT NULL,
                    creado_en    DATETIME2     NOT NULL DEFAULT GETDATE()
                )
            """);
            log.info("DatabaseInitializer: tabla cuentas_cobro verificada/creada OK");
        } catch (Exception e) {
            log.error("DatabaseInitializer: error al crear tabla cuentas_cobro: {}", e.getMessage());
        }
    }
}
