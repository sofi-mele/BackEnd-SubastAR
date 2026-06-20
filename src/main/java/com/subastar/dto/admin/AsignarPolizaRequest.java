package com.subastar.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AsignarPolizaRequest {

    @JsonProperty("numero_poliza")
    @NotBlank
    private String numeroPoliza;

    @JsonProperty("compania_seguro")
    @NotBlank
    private String companiaSeguro;

    @JsonProperty("valor_asegurado")
    @NotNull
    private BigDecimal valorAsegurado;
}
