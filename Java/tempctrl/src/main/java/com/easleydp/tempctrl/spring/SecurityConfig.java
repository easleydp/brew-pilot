package com.easleydp.tempctrl.spring;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import com.easleydp.tempctrl.spring.config.AppProperties;
import com.easleydp.tempctrl.domain.Utils;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private AppProperties appProps;

    private static final int loginFailureDelayMs_lowest = 1 * 1000;
    private static final int loginFailureDelayMs_highest = 64 * 1000; // Some power of 2 greater than lowest

    private final AtomicInteger loginFailureDelayMs = new AtomicInteger(loginFailureDelayMs_lowest);

    @Bean
    public UserDetailsService userDetailsService() throws Exception {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        // For debug: String pwdHash = encoder().encode("?????");
        String pwdHash = appProps.getPasswordHashes().getGuest().replaceAll("#dollar#", "\\$"); // For running from
                                                                                                // vscode
        Assert.state(pwdHash != null && pwdHash.length() > 0, "Guest password not specified");
        manager.createUser(User.withUsername("guest").password(pwdHash).roles("USER").build());

        pwdHash = appProps.getPasswordHashes().getAdmin().replaceAll("#dollar#", "\\$"); // For running from vscode
        Assert.state(pwdHash != null && pwdHash.length() > 0, "Admin password not specified");
        manager.createUser(User.withUsername("admin").password(pwdHash).roles("USER", "ADMIN").build());

        return manager;
    }

    @Order(1)
    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        configureRoleSecurity(http, "/admin/**", "ADMIN");
        return http.build();
    }

    @Order(2)
    @Bean
    public SecurityFilterChain guestSecurityFilterChain(HttpSecurity http) throws Exception {
        configureRoleSecurity(http, "/guest/**", "USER");
        return http.build();
    }

    @Order(3)
    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        configureRoleSecurity(http, "/actuator/**", "ADMIN");
        return http.build();
    }

    @Order(4)
    @Bean
    public SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http, EmailService emailService)
            throws Exception {
        // @formatter:off
        http
                .authorizeRequests(authorizeRequests ->
                    authorizeRequests
                        .antMatchers("/csrf").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin()
                    .loginProcessingUrl("/login").usernameParameter("username").passwordParameter("password").permitAll()
                        .successHandler(successHandler(emailService))
                        .failureHandler(failureHandler(emailService))
            .and()
                .exceptionHandling()
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
            .and()
                .logout().logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
            .and()
                .csrf().csrfTokenRepository(csrfTokenRepository())
            .and()
                .addFilterAfter(csrfHeaderFilter(), CsrfFilter.class)
            ;
        // @formatter:on
        return http.build();
    }

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    private void configureRoleSecurity(HttpSecurity http, String antPattern, String role) throws Exception {
        // @formatter:off
        http
            .antMatcher(antPattern)
                .authorizeRequests()
                .anyRequest().hasRole(role)
            .and().exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            .and().csrf().csrfTokenRepository(csrfTokenRepository())
            .and().addFilterAfter(csrfHeaderFilter(), CsrfFilter.class)
        ;
        // @formatter:on
    }

    private AuthenticationSuccessHandler successHandler(EmailService emailService) {
        return new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                    Authentication authentication) throws IOException, ServletException {
                boolean isAdmin = authentication.getAuthorities().stream()
                        .filter(auth -> auth.getAuthority().equals("ROLE_ADMIN")).findFirst().isPresent();

                logger.info("Successful login ({})", request.getParameter("username"));
                if (!isAdmin) {
                    emailService.sendSimpleMessage("BrewPilot guest login", "");
                }

                // We use successful logins as a hook to decay any accumulated login failure
                // delay.
                if (loginFailureDelayMs.getAndSet(loginFailureDelayMs_lowest) > loginFailureDelayMs_lowest) {
                    logger.debug("Login failure delay reset");
                }

                response.setContentType("application/json");
                response.setStatus(200);
                response.getWriter().write("{\"result\":\"OK\", \"isAdmin\":" + isAdmin + "}");
            }
        };
    }

    private AuthenticationFailureHandler failureHandler(EmailService emailService) {
        return new AuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                    AuthenticationException e) throws IOException, ServletException {
                logger.warn("Login failure ({})", request.getParameter("username"));
                emailService.sendSimpleMessage("BrewPilot login failure", request.getParameter("username"));

                // Apply an increasing delay to failed login attempts.
                int delayMs = loginFailureDelayMs.get();
                Utils.sleep(delayMs);
                int nextDelayMs = loginFailureDelayMs.updateAndGet(
                        current -> Math.min(current * 2, loginFailureDelayMs_highest));
                if (nextDelayMs > delayMs) {
                    logger.warn("Login failure delay increased to {}", nextDelayMs);
                }

                response.setContentType("application/json");
                response.setStatus(401);
                response.getWriter().write("{\"result\":\"UNAUTHORIZED\",\"message\":\"Authentication failure\"}");
            }
        };
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, e) -> {
            response.setContentType("application/json");
            response.setStatus(401);
            response.getWriter().write("{\"result\":\"UNAUTHORIZED\",\"message\":\"Not authenticated\"}");
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, e) -> {
            response.setContentType("application/json");
            response.setStatus(403);
            response.getWriter().write("{\"result\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
        };
    }

    private Filter csrfHeaderFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrf != null) {
                    Cookie cookie = WebUtils.getCookie(request, "XSRF-TOKEN");
                    String token = csrf.getToken();
                    if (cookie == null || token != null && !token.equals(cookie.getValue())) {
                        cookie = new Cookie("XSRF-TOKEN", token);
                        cookie.setPath("/");
                        response.addCookie(cookie);
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }
}
