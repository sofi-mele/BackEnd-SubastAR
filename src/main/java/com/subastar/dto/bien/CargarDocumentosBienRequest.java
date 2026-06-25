package com.subastar.dto.bien;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CargarDocumentosBienRequest {
    @NotNull
    @JsonProperty("declara_propiedad")
    private Boolean declaraPropiedad;

    @NotNull
    @JsonProperty("acepta_devolucion_con_cargo")
    private Boolean aceptaDevolucionConCargo;
}
