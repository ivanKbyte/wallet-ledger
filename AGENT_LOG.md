# Agent Log

## 1. What I asked Claude to do

I orchestrated Claude through six sequential phases (Bootstrap, Schema, Service, Web, Tests, Docs) to keep diffs reviewable. I gave it one strict architectural constraint upfront: handle concurrency at the PostgreSQL layer using atomic UPDATE queries and UNIQUE constraints, expressly forbidding JPA `@Version` optimistic locking and Hibernate retry loops.

## 2. Where Claude got it wrong

**Idempotency error mapping:** Claude routed the idempotency `DataIntegrityViolationException` to a 409 Conflict in `GlobalExceptionHandler`. This violated the spec, which requires returning the original transaction with a 200 OK. Catching this inside the `@Transactional` service was impossible; once the constraint fires, the transaction is rollback-only and further reads crash. I moved the try/catch up to the controller to run a clean, separate read-only query.

**Testcontainers on macOS:** Claude treated my Docker connection failure as an env-var issue, injecting `DOCKER_API_VERSION`. It missed that Testcontainers' shaded `docker-java` reads only from the classpath. After I added `docker-java.properties`, Claude pointed it at `docker.raw.sock`. This fixed the API but broke Ryuk: on macOS, the API socket and volume-mount socket differ, and Ryuk tried to mount the raw socket inside its Linux VM. I disabled Ryuk in Gradle to get the tests green.

## 3. Something I decided myself

Claude annotated the native UPDATE queries with `@Modifying(clearAutomatically = true)`. That clears the persistence context after the query, but doesn't flush before it, allowing a pending `Transaction` INSERT to race the balance update. I added `flushAutomatically = true` so the INSERT hits the DB first. I also pinned `@PrePersist` timestamps to `OffsetDateTime.now(ZoneOffset.UTC)` to prevent JVM timezone drift.

## 4. Almost shipped because I trusted too quickly

Claude's initial `Wallet` entity included a `setBalance()` setter — standard JPA boilerplate. On a second pass, I caught the footgun: any caller could do a naive read-modify-write through that setter, silently bypassing the atomic `debitBalance` query my entire concurrency strategy relied on. I deleted the setter to enforce immutability.
