package com.leyoda.wallet.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Transaction() {}

    public Transaction(UUID walletId, TransactionType type, long amount, String reference, String idempotencyKey) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public TransactionType getType() { return type; }
    public long getAmount() { return amount; }
    public String getReference() { return reference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
