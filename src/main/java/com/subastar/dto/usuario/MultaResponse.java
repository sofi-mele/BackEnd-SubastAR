package com.subastar.dto.usuario;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MultaResponse {
    private Integer id;
    private BigDecimal monto;
    private String estado;
    private LocalDateTime fecha;
    private String motivo;
    @JsonProperty("registro_id")
    private Integer registroId;
    @JsonProperty("compra_id")
    private Integer compraId;
    @JsonProperty("descripcion_compra")
    private String descripcionCompra;
}
