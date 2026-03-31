package com.searchapioptimization.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductCreateRequest(
        @NotBlank String name,
        @NotBlank String brand,
        @NotBlank String category,
        @NotNull @Positive Long price,
        Long salesCount,
        Boolean promoted
) {
}
