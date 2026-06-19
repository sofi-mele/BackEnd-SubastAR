package com.subastar.dto.usuario;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ParticipacionPerdidaResponse {
    private Integer itemId;
    private String nombreProducto;
    private String descripcion;
    private BigDecimal precioBase;
    private BigDecimal miMejorPuja;
    private BigDecimal precioFinalVenta;
    private String nombreGanador;
    private LocalDateTime fechaPuja;
    private Integer subastaId;
}
