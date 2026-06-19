IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.subastas_extra')
      AND name = 'fecha_inicio_lote'
)
BEGIN
    ALTER TABLE subastas_extra ADD fecha_inicio_lote DATETIME NULL;
END

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.subastas_extra')
      AND name = 'fecha_ultima_puja'
)
BEGIN
    ALTER TABLE subastas_extra ADD fecha_ultima_puja DATETIME NULL;
END
