package com.leyoda.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateTransactionRequest(
        @Positive(message = "amount must be positive") long amount,
        @NotBlank(message = "reference must not be blank") String reference,
        @NotBlank(message = "idempotencyKey must not be blank") String idempotencyKey
) {}
