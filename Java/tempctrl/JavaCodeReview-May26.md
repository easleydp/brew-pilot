# Gemini-CLI code review issues (3 May 2026)

## Security Configuration (SecurityConfig.java)

- Static Hack: The use of a static field for EmailService in SecurityConfig is a red flag. It is recommended to inject the service into the authentication handlers as beans rather than using a static reference.
- Naming Consistency: GuestConfigurationAdapter protecting /admin/** and AdminConfigurationAdapter protecting /guest/** is confusing. Consider renaming them to reflect the paths or roles they protect (e.g., AdminPathConfig and GuestPathConfig).
- CSRF Handling: CSRF is disabled for form login but enabled for other paths. Ensure this consistency aligns with the frontend's requirements.

## Email Services (EmailServiceImpl.java)

- @Async on Private Method: In EmailServiceImpl, the @Async annotation is on a private method \_sendSimpleMessage called from within the same class. Due to Spring's proxy-based AOP, this call will not be asynchronous. To fix this, move the @Async annotation to the public sendSimpleMessage method.
- Retry and Async Synergy: Combining @Retryable and @Async is a good practice for transient SMTP issues, but ensure the retry configuration is on the public method to be intercepted by the proxy.

## Code Quality and Maintenance

- Broad Exception Catching: Avoid catching Throwable in CollectReadingsScheduler. Catching Exception is usually sufficient and safer.
- Configuration Management: PropertyUtils uses a static Environment reference. While acceptable for a small project, consider using standard Spring property injection (@Value or @ConfigurationProperties) to improve testability.
