package com.subastar.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    // Campos opcionales para SeguroExtra
    @JsonProperty("cobertura")
    private String cobertura;

    @JsonProperty("vigencia_desde")
    private LocalDate vigenciaDesde;

    @JsonProperty("vigencia_hasta")
    private LocalDate vigenciaHasta;

    @JsonProperty("contacto_telefono")
    private String contactoTelefono;

    @JsonProperty("contacto_email")
    private String contactoEmail;

    @JsonProperty("contacto_web")
    private String contactoWeb;
}
