package com.leyoda.wallet.dto;

import com.leyoda.wallet.entity.Wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponse(UUID id, UUID userId, long balance, OffsetDateTime createdAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance(), wallet.getCreatedAt());
    }
}
