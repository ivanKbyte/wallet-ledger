package com.leyoda.wallet.dto;

import java.util.List;

public record PagedTransactionResponse(
        List<TransactionResponse> content,
        long totalElements,
        int page,
        int size
) {}
