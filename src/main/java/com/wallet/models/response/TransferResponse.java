package com.wallet.models.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wallet.entities.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for transfer operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {

    @JsonProperty("transferId")
    private String transferId;

    @JsonProperty("fromWalletId")
    private String fromWalletId;

    @JsonProperty("toWalletId")
    private String toWalletId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("status")
    private TransferStatus status;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("message")
    private String message;
}

