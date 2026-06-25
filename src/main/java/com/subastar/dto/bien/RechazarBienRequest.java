package com.subastar.dto.bien;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RechazarBienRequest {

    @NotBlank(message = "El motivo de rechazo es obligatorio")
    private String motivo;

    @NotNull(message = "El costo de devolución es obligatorio")
    @JsonProperty("costo_devolucion")
    private BigDecimal costoDevolucion;
}
