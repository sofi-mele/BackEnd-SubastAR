package com.subastar.dto.bien;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AceptarBienRequest {

    @JsonProperty("precio_base")
    private BigDecimal precioBase;

    @JsonProperty("comision")
    private BigDecimal comision;

    @JsonProperty("costo_envio")
    private BigDecimal costoEnvio;
}
