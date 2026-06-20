package com.subastar.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CrearSubastaRequest {

    @NotBlank
    private String nombre;

    @NotNull
    private LocalDate fecha;

    @NotNull
    private LocalTime hora;

    @NotBlank
    private String moneda;

    @JsonProperty("categoria_requerida")
    @NotBlank
    private String categoriaRequerida;

    @JsonProperty("rematador_id")
    @NotNull
    private Integer rematadorId;
}
