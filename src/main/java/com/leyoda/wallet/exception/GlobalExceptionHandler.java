package com.leyoda.wallet.exception;

import com.leyoda.wallet.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleWalletNotFound(WalletNotFoundException e) {
        return new ErrorResponse("WALLET_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleWalletAlreadyExists(WalletAlreadyExistsException e) {
        return new ErrorResponse("WALLET_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInsufficientBalance(InsufficientBalanceException e) {
        return new ErrorResponse("INSUFFICIENT_BALANCE", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorResponse("VALIDATION_ERROR", message);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(Exception e) {
        return new ErrorResponse("VALIDATION_ERROR", e.getMessage());
    }
}
