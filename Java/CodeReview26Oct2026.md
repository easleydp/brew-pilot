# Java code review — 26 Oct 2026

I reviewed the Java backend in [Java/tempctrl/src/main/java](./tempctrl/src/main/java) and did not modify any production code. This is a code review only.

## Severe

### [FIXED] 1) CSRF is disabled for the authenticated web app

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java)

- In `FormLoginWebSecurityConfigurerAdapter.configure()`, the app calls `http.csrf().disable()`.
- This disables cross-site request forgery protection for the session-based login flow and the state-changing endpoints behind it.
- Since the app relies on cookie-backed authentication and exposes admin endpoints, this creates a real risk of cross-site requests being accepted while a valid browser session is active.
- The risk is not theoretical: the frontend is a browser app, and the backend uses JSON responses plus role-based access. This is a classic CSRF exposure pattern.

### [FIXED] 2) Live gyle state is mutated from unvalidated client payloads

Files: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/ChamberController.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/ChamberController.java), [Java/tempctrl/src/main/java/com/easleydp/tempctrl/domain/Gyle.java](./tempctrl/src/main/java/com/easleydp/tempctrl/domain/Gyle.java)

- `updateLatestGyleProfile()` and `updateLatestGyle()` accept full DTOs from the HTTP request and copy them directly onto the live `Gyle` object with `BeanUtils.copyProperties()`.
- There is no server-side validation before persisting the object to disk via `latestGyle.updateJsonFile()`.
- A compromised client or buggy frontend can submit impossible values for `dtStarted`, `dtEnded`, temperature profile points, `mode`, or `tHold`, then the scheduler will continue to act on that data as if it were authoritative.
- This is especially serious because the system is modelled around time-driven fermentation logic and cold-crash detection; invalid control data can produce incorrect chamber parameters or historical corruption with no guardrail in the backend.

## Moderate

### [FIXED] 3) Static mutable state in `SecurityConfig` bypasses Spring idioms and creates race-prone global state

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java)

- The class stores `_emailService` in a static field and assigns it inside `userDetailsService()`.
- `successHandler()` and `failureHandler()` then read the static field later to send email notifications.
- This pattern is brittle in a multi-threaded app: concurrent logins, bean initialisation ordering, or tests can create null/stale references. It also hides dependencies from Spring, making the code harder to reason about and harder to test.
- It is not a safe substitute for proper dependency injection.

### [FIXED] 4) `@Async` is applied to a private method, so the async behaviour is effectively bypassed

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/EmailServiceImpl.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/EmailServiceImpl.java)

- `@Async` is declared on `_sendSimpleMessage()`, which is a private method invoked from inside the same class.
- Spring AOP cannot intercept a call from inside the same object instance; the annotation therefore does not have the intended effect.
- Because `sendSimpleMessage()` is the public method that calls the private helper, the retry/async intent is split across two methods in a way that can easily produce unexpected synchronous behaviour.
- This is a correctness issue in the application’s email delivery path and can delay request handling or produce confusing failure behaviour.

### [FIXED] 5) `CollectReadingsScheduler` catches `Throwable`, which masks fatal JVM-level failures

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/CollectReadingsScheduler.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/CollectReadingsScheduler.java)

- The method `collectReadingsForChamber()` catches `Throwable`, not just `Exception`.
- This catches serious failures such as `OutOfMemoryError`, `StackOverflowError`, or other JVM-level errors and treats them like recoverable business exceptions.
- The code then logs and continues, which can leave the app in a partially broken state while the scheduler keeps running. That is the wrong failure boundary for a control loop that interacts with external hardware and persisted history.

### [FIXED] 6) `PropertyUtils` is a static global singleton over Spring `Environment`

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/domain/PropertyUtils.java](./tempctrl/src/main/java/com/easleydp/tempctrl/domain/PropertyUtils.java)

- The class stores a mutable static `Environment` and exposes a large set of static getter methods.
- This couples the domain model to a global external bean and makes unit testing fragile. It also makes null-safety and initialization ordering a runtime concern rather than a compile-time contract.
- Several methods make assumptions that `env` has already been set by `TempctrlApplication.init()`; if that setup is skipped or changed, the app fails late with an NPE or `IllegalStateException` instead of a clear application-level error.
- This pattern is a poor fit for a stateful control system that should be easy to validate in isolation.

### [FIXED] 7) The gyle creation path can throw a null-pointer error on a fresh chamber

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/domain/Chamber.java](./tempctrl/src/main/java/com/easleydp/tempctrl/domain/Chamber.java)

- `constructNextGyle()` does `int nextId = latestGyle.id + 1;` without checking whether `latestGyle` is non-null.
- A chamber directory can exist without any valid gyle entries, especially during startup or after data migration. In that case, the code crashes before it can create the first gyle record.
- This is a real data-setup bug that can prevent legitimate workflow steps even if the rest of the system is otherwise healthy.

### 8) OS command execution is a command-injection sink with no validation

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/util/OsCommandExecuter.java](./tempctrl/src/main/java/com/easleydp/tempctrl/util/OsCommandExecuter.java)

- `ProcessBuilder.command(command)` executes an arbitrary command array provided by the caller.
- The helper does not validate the command or arguments, and it is used from status/health code paths that may eventually read values from external system configuration or runtime state.
- This is not currently an exploitation issue in the static code path, but it is a dangerous API design for a backend service that may be expanded or used with untrusted input in the future.
- The command should be constrained to a fixed allow-list or validated before execution.

## Minor

### [FIXED] 9) Adapter naming is confusing and misleading

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java)

- `GuestConfigurationAdapter` protects `/admin/**`, and `AdminConfigurationAdapter` protects `/guest/**`.
- The class names do not match the path they guard or the role they apply; they are easy to read backwards and therefore easy to misconfigure.
- This is not a functional bug today, but it is a maintainability hazard in a security-heavy class.

### [FIXED] 10) `PropertyUtils` parsing was not null-safe for absent or blank configuration

Resolved by replacing the custom static property parsing with typed Spring configuration binding via [AppProperties](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/config/AppProperties.java).

### 11) The codebase mixes admin/guest terminology in ways that are easy to invert accidentally

File: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/SecurityConfig.java)

- The security config is sectioned by URL path, but the adapter class names imply the opposite of what they are protecting.
- This is a cognitive bug that makes future changes more error-prone, especially in a project with role-specific endpoints and user-type checks.

### 12) Several code paths use broad string concatenation and ad hoc message formatting where clearer typed DTOs would be safer

Files: [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/StatusController.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/StatusController.java), [Java/tempctrl/src/main/java/com/easleydp/tempctrl/spring/EmailMessageScheduler.java](./tempctrl/src/main/java/com/easleydp/tempctrl/spring/EmailMessageScheduler.java)

- A few status and email messages are assembled as hand-written strings rather than typed payloads or domain objects.
- This is not a runtime bug by itself, but it makes the API contract less explicit and increases the risk of silent schema drift between the Java backend and the UI.

## Overall assessment

The backend is ambitious and modular, but the security model and control-state mutation paths are the biggest concerns. The most important problems are the disabled CSRF protection and the lack of validation around live gyle updates. The design is workable, but it needs a more defensive boundary around persisted state and the web authentication layer before it should be treated as production-ready.
