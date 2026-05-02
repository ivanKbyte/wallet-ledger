package com.leyoda.wallet.service;

import com.leyoda.wallet.entity.Transaction;
import com.leyoda.wallet.entity.TransactionType;
import com.leyoda.wallet.entity.Wallet;
import com.leyoda.wallet.exception.InsufficientBalanceException;
import com.leyoda.wallet.exception.WalletAlreadyExistsException;
import com.leyoda.wallet.exception.WalletNotFoundException;
import com.leyoda.wallet.repository.TransactionRepository;
import com.leyoda.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Wallet createWallet(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            throw new WalletAlreadyExistsException(userId);
        }
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        try {
            return walletRepository.saveAndFlush(wallet);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent POST /wallets for the same user — the DB unique constraint
            // on user_id caught what our pre-check missed.
            throw new WalletAlreadyExistsException(userId);
        }
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
    }

    @Transactional
    public Transaction credit(UUID userId, long amount, String reference, String idempotencyKey) {
        Wallet wallet = getWallet(userId);

        return transactionRepository
                .findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey)
                .orElseGet(() -> {
                    Transaction tx = new Transaction(wallet.getId(), TransactionType.CREDIT, amount, reference, idempotencyKey);
                    transactionRepository.saveAndFlush(tx);
                    walletRepository.creditBalance(wallet.getId(), amount);
                    return tx;
                });
    }

    @Transactional
    public Transaction debit(UUID userId, long amount, String reference, String idempotencyKey) {
        Wallet wallet = getWallet(userId);

        return transactionRepository
                .findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey)
                .orElseGet(() -> {
                    int updated = walletRepository.debitBalance(wallet.getId(), amount);
                    if (updated == 0) {
                        // Before throwing, check if a concurrent request with the same
                        // idempotency key already committed the debit (which drained the balance).
                        // If so, return that transaction for an idempotent 200 response.
                        return transactionRepository
                                .findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey)
                                .orElseThrow(() -> {
                                    long actualBalance = walletRepository.findByUserId(userId)
                                            .map(Wallet::getBalance)
                                            .orElse(0L);
                                    return new InsufficientBalanceException(actualBalance, amount);
                                });
                    }
                    Transaction tx = new Transaction(wallet.getId(), TransactionType.DEBIT, amount, reference, idempotencyKey);
                    transactionRepository.saveAndFlush(tx);
                    return tx;
                });
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionByIdempotencyKey(UUID userId, String idempotencyKey) {
        Wallet wallet = getWallet(userId);
        return transactionRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency fallback lookup found no transaction for key " + idempotencyKey
                                + " on wallet " + wallet.getId()
                                + " — a DataIntegrityViolationException was caught but the conflicting row is not visible"));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getTransactions(UUID userId, Pageable pageable) {
        Wallet wallet = getWallet(userId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
    }
}
