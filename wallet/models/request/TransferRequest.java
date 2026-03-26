package com.wallet.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {

    @NotBlank(message = "Idempotency key is required")
    @JsonProperty("idempotencyKey")
    private String idempotencyKey;

    @NotBlank(message = "From wallet ID is required")
    @JsonProperty("fromWalletId")
    private String fromWalletId;

    @NotBlank(message = "To wallet ID is required")
    @JsonProperty("toWalletId")
    private String toWalletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @JsonProperty("amount")
    private BigDecimal amount;
}

