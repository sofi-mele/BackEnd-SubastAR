CREATE TABLE cuentas_cobro (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    cliente_id   INT           NOT NULL,
    nombre_banco NVARCHAR(200) NOT NULL,
    cbu_iban     NVARCHAR(100) NOT NULL,
    pais         NVARCHAR(100) NOT NULL,
    moneda       NVARCHAR(10)  NOT NULL,
    creado_en    DATETIME2     NOT NULL DEFAULT GETDATE()
);
