package com.subastar.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ModificarSubastaRequest {

    private String nombre;
    private String fecha;
    private String hora;
    private String moneda;

    @JsonProperty("categoria_requerida")
    private String categoriaRequerida;

    @JsonProperty("rematador_nombre")
    private String rematadorNombre;
}
