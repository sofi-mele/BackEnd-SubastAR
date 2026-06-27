package com.subastar.dto.compra;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PreferenciaEntregaRequest {

    @NotBlank(message = "El tipo de entrega es obligatorio (retiro o envio)")
    private String tipo;

    private String direccion;
}
