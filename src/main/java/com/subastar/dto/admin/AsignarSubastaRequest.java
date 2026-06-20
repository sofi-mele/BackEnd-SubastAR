package com.subastar.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AsignarSubastaRequest {

    @JsonProperty("subasta_id")
    @NotNull
    private Integer subastaId;
}
