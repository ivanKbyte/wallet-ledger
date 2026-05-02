package com.leyoda.wallet.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(long balance, long amount) {
        super("Wallet balance is " + balance + ", requested debit of " + amount);
    }
}
