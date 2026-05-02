# Agent Log

## 1. What I asked Claude to do

I used Claude to scaffold the service and controller layers for the wallet ledger, with one specific constraint: handle idempotency for `/credit` and `/debit` without JPA `@Version` optimistic locking. I wanted the concurrency story to live at the SQL layer — UNIQUE constraint plus atomic UPDATE — not inside Hibernate retry loops.

## 2. Where Claude got it wrong

**Idempotency error mapping.** Claude routed `DataIntegrityViolationException` from the `(wallet_id, idempotency_key)` UNIQUE constraint to a 409 Conflict in `GlobalExceptionHandler`. That violates the spec — a duplicate must return 200 with the original transaction. The fix isn't trivial: once the constraint fires inside an `@Transactional` service method, the transaction is rollback-only and any subsequent read inside it crashes. I moved the try/catch up to the controller, where after the failed write a separate read-only query fetches the original transaction cleanly.

**Testcontainers on macOS.** Claude treated the Docker connection as a vanilla env-var problem and injected `DOCKER_API_VERSION` into Gradle. It missed that Testcontainers ships a shaded copy of `docker-java` that reads its API version only from the classpath — so I had to drop a `docker-java.properties` into `src/test/resources`. The next attempt pointed at `docker.raw.sock`, which fixed the API call but broke Ryuk: on macOS the daemon API socket and the volume-mount socket aren't the same path, and Ryuk tried to mount `docker.raw.sock` inside its Linux VM. I disabled Ryuk in `build.gradle.kts` and the test suite went green.

## 3. Something I decided myself

Claude annotated the native UPDATE queries with `@Modifying(clearAutomatically = true)`. That clears the persistence context *after* the query runs but doesn't flush *before* it, so a pending `Transaction` INSERT can race the balance update. I added `flushAutomatically = true` so the INSERT hits the DB first. I also pinned `@PrePersist` timestamps to `OffsetDateTime.now(ZoneOffset.UTC)` — without it, timestamps drift with whatever timezone the JVM happens to be in.

## 4. Almost shipped because I trusted too quickly

Claude's first cut of the `Wallet` entity included a `setBalance()` setter — innocuous-looking JPA boilerplate. On a second pass I caught it: any caller could do a read-modify-write through that setter and silently bypass the atomic `debitBalance` query the whole concurrency story rests on. I dropped the setter so balance changes can only flow through the repository UPDATE.
