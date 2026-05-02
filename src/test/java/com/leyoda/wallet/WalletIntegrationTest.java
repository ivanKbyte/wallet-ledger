package com.leyoda.wallet;

import com.leyoda.wallet.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@SuppressWarnings("null")
public class WalletIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate restTemplate;

    private UUID userId = UUID.randomUUID();
    private final HttpHeaders headers = new HttpHeaders();

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        headers.clear();
        headers.set("X-User-Id", userId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    private WalletResponse createWallet() {
        var resp = restTemplate.exchange(
                "/wallets", HttpMethod.POST, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(resp.getBody());
    }

    private TransactionResponse credit(long amount, String ref, String idempotencyKey) {
        var body = new CreateTransactionRequest(amount, ref, idempotencyKey);
        var resp = restTemplate.exchange(
                "/wallets/me/credit", HttpMethod.POST, new HttpEntity<>(body, headers), TransactionResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(resp.getBody());
    }

    private ResponseEntity<String> debitRaw(UUID uid, long amount, String ref, String idempotencyKey) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Id", uid.toString());
        h.setContentType(MediaType.APPLICATION_JSON);
        var body = new CreateTransactionRequest(amount, ref, idempotencyKey);
        return restTemplate.exchange(
                "/wallets/me/debit", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    @Test
    public void happyPath_creditAndDebit_balanceCorrect() {
        createWallet();
        credit(100, "initial-load", "key-credit-1");

        var debitBody = new CreateTransactionRequest(30, "purchase", "key-debit-1");
        var debitResp = restTemplate.exchange(
                "/wallets/me/debit", HttpMethod.POST, new HttpEntity<>(debitBody, headers), TransactionResponse.class);
        assertThat(debitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(debitResp.getBody()).amount()).isEqualTo(30);

        var walletResp = restTemplate.exchange(
                "/wallets/me", HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(walletResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(walletResp.getBody()).balance()).isEqualTo(70);
    }

    @Test
    public void debit_insufficientBalance_returns422() {
        createWallet();

        var resp = restTemplate.exchange(
                "/wallets/me/debit", HttpMethod.POST,
                new HttpEntity<>(new CreateTransactionRequest(50, "buy", "key-insuf-1"), headers),
                ErrorResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(Objects.requireNonNull(resp.getBody()).error()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    public void creditIdempotency_sameKeyTwice_onlyOneTransactionAndBalanceUnchanged() {
        createWallet();

        var tx1 = credit(100, "load", "key-idem-1");
        var tx2 = credit(100, "load", "key-idem-1");

        assertThat(tx1.id()).isEqualTo(tx2.id());

        var walletResp = restTemplate.exchange(
                "/wallets/me", HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(Objects.requireNonNull(walletResp.getBody()).balance()).isEqualTo(100);
    }

    @Test
    public void createWalletTwice_secondReturns409() {
        createWallet();

        var second = restTemplate.exchange(
                "/wallets", HttpMethod.POST, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(second.getBody()).error()).isEqualTo("WALLET_ALREADY_EXISTS");
    }

    @Test
    public void concurrency_tenDebitsTwentyEach_exactlyFiveSucceedAndBalanceIsZero() throws InterruptedException {
        createWallet();
        credit(100, "initial-load", "key-init");

        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        UUID testUserId = userId;

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    var resp = debitRaw(testUserId, 20, "debit-ref-" + idx, "test-debit-" + idx);
                    statusCodes.add(resp.getStatusCode().value());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        assertThat(statusCodes).hasSize(threadCount);
        assertThat(statusCodes.stream().filter(s -> s == 200).count()).isEqualTo(5);
        assertThat(statusCodes.stream().filter(s -> s == 422).count()).isEqualTo(5);
        assertThat(statusCodes.stream().noneMatch(s -> s >= 500)).isTrue();

        var walletResp = restTemplate.exchange(
                "/wallets/me", HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(Objects.requireNonNull(walletResp.getBody()).balance()).isEqualTo(0);
    }

    @Test
    public void getWallet_whenNoWalletExists_returns404() {
        var resp = restTemplate.exchange(
                "/wallets/me", HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Objects.requireNonNull(resp.getBody()).error()).isEqualTo("WALLET_NOT_FOUND");
    }

    @Test
    public void credit_invalidBody_returns400Validation() {
        createWallet();

        var zeroAmount = restTemplate.exchange(
                "/wallets/me/credit", HttpMethod.POST,
                new HttpEntity<>(new CreateTransactionRequest(0, "ref", "key-zero"), headers),
                ErrorResponse.class);
        assertThat(zeroAmount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(zeroAmount.getBody()).error()).isEqualTo("VALIDATION_ERROR");

        var blankReference = restTemplate.exchange(
                "/wallets/me/credit", HttpMethod.POST,
                new HttpEntity<>(new CreateTransactionRequest(10, "  ", "key-blank"), headers),
                ErrorResponse.class);
        assertThat(blankReference.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(blankReference.getBody()).error()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    public void getTransactions_returnsHistoryNewestFirst() {
        createWallet();
        credit(100, "first",  "key-tx-1");
        credit(50,  "second", "key-tx-2");
        credit(25,  "third",  "key-tx-3");

        var resp = restTemplate.exchange(
                "/wallets/me/transactions?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(headers), PagedTransactionResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = Objects.requireNonNull(resp.getBody());
        assertThat(body.totalElements()).isEqualTo(3);
        assertThat(body.content()).extracting(TransactionResponse::reference)
                .containsExactly("third", "second", "first");
    }
}
