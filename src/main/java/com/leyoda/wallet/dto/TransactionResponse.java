package com.leyoda.wallet.dto;

import com.leyoda.wallet.entity.Transaction;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        String type,
        long amount,
        String reference,
        String idempotencyKey,
        OffsetDateTime createdAt
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getWalletId(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getReference(),
                tx.getIdempotencyKey(),
                tx.getCreatedAt());
    }
}
