package com.leyoda.wallet.controller;

import com.leyoda.wallet.dto.*;
import com.leyoda.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@RequestHeader("X-User-Id") UUID userId) {
        return WalletResponse.from(walletService.createWallet(userId));
    }

    @GetMapping("/me")
    public WalletResponse getWallet(@RequestHeader("X-User-Id") UUID userId) {
        return WalletResponse.from(walletService.getWallet(userId));
    }

    @PostMapping("/me/credit")
    public TransactionResponse credit(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateTransactionRequest request) {
        try {
            return TransactionResponse.from(
                    walletService.credit(userId, request.amount(), request.reference(), request.idempotencyKey()));
        } catch (DataIntegrityViolationException e) {
            return TransactionResponse.from(
                    walletService.getTransactionByIdempotencyKey(userId, request.idempotencyKey()));
        }
    }

    @PostMapping("/me/debit")
    public TransactionResponse debit(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateTransactionRequest request) {
        try {
            return TransactionResponse.from(
                    walletService.debit(userId, request.amount(), request.reference(), request.idempotencyKey()));
        } catch (DataIntegrityViolationException e) {
            return TransactionResponse.from(
                    walletService.getTransactionByIdempotencyKey(userId, request.idempotencyKey()));
        }
    }

    @GetMapping("/me/transactions")
    public PagedTransactionResponse getTransactions(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        var txPage = walletService.getTransactions(userId, PageRequest.of(page, size));
        return new PagedTransactionResponse(
                txPage.getContent().stream().map(TransactionResponse::from).toList(),
                txPage.getTotalElements(),
                page,
                size);
    }
}
