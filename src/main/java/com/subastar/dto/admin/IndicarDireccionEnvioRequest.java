package com.subastar.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IndicarDireccionEnvioRequest {
    @NotBlank(message = "La dirección de envío no puede estar vacía")
    @JsonProperty("direccion")
    private String direccion;
}
