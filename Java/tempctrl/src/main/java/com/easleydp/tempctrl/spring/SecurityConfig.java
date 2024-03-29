package com.easleydp.tempctrl.spring;

import java.io.IOException;

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
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
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

import com.easleydp.tempctrl.domain.Utils;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private Environment env;

    @Autowired
    private EmailService emailService;

    // Hack. Allow static code to access the above Spring component.
    private static EmailService _emailService = null;

    private static final int loginFailureDelayMs_lowest = 1 * 1000;
    private static final int loginFailureDelayMs_highest = 64 * 1000; // Some power of 2 greater than lowest
    private static int loginFailureDelayMs = loginFailureDelayMs_lowest;

    @Bean
    public UserDetailsService userDetailsService() throws Exception {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        // For debug: String pwdHash = encoder().encode("?????");
        String pwdHash = env.getProperty("pwdhash.guest").replaceAll("#dollar#", "\\$"); // For running from vscode
        Assert.state(pwdHash != null && pwdHash.length() > 0, "Guest password not specified");
        manager.createUser(User.withUsername("guest").password(pwdHash).roles("USER").build());

        pwdHash = env.getProperty("pwdhash.admin").replaceAll("#dollar#", "\\$"); // For running from vscode
        Assert.state(pwdHash != null && pwdHash.length() > 0, "Admin password not specified");
        manager.createUser(User.withUsername("admin").password(pwdHash).roles("USER", "ADMIN").build());

        // Hack
        _emailService = emailService;

        return manager;
    }

    @Configuration
    @Order(1)
    public static class GuestConfigurationAdapter extends MyWebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            configure(http, "/admin/**", "ADMIN");
        }
    }

    @Configuration
    @Order(2)
    public static class AdminConfigurationAdapter extends MyWebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            configure(http, "/guest/**", "USER");
        }
    }

    @Configuration
    @Order(3)
    public static class ActuatorConfigurationAdapter extends MyWebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            configure(http, "/actuator/**", "ADMIN");
        }
    }

// @formatter:off
// Uncomment this to disable authentication for actuator
//    @Configuration
//    @Order(3)
//    public static class ActuatorConfigurationAdapter extends MyWebSecurityConfigurerAdapter
//    {
//        @Override
//        protected void configure(HttpSecurity http) throws Exception {
//            http.authorizeRequests()
//                    .antMatchers("/actuator/**")
//                    .permitAll()
//                .anyRequest()
//                    .authenticated();
//        }
//    }
// @formatter:on

    @Configuration
    public static class FormLoginWebSecurityConfigurerAdapter extends MyWebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // @formatter:off
            http
                    .csrf().disable()
                    .authorizeRequests(authorizeRequests ->
                        authorizeRequests
                            .anyRequest().authenticated()
                    )
                    .formLogin()
                        .loginProcessingUrl("/login").usernameParameter("username").passwordParameter("password").permitAll()
                            .successHandler(successHandler())
                            .failureHandler(failureHandler())
                .and()
                    .exceptionHandling()
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                .and()
                    .logout().logoutSuccessHandler((new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)))
                ;
            // @formatter:on
        }

        private AuthenticationSuccessHandler successHandler() {
            return new AuthenticationSuccessHandler() {
                @Override
                public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                        Authentication authentication) throws IOException, ServletException {
                    boolean isAdmin = authentication.getAuthorities().stream()
                            .filter(auth -> auth.getAuthority().equals("ROLE_ADMIN")).findFirst().isPresent();

                    logger.info("Successful login ({})", request.getParameter("username"));
                    if (!isAdmin) {
                        _emailService.sendSimpleMessage("BrewPilot guest login", "");
                    }

                    // We use successful logins as a hook to decay any accumulated login failure
                    // delay.
                    if (loginFailureDelayMs > loginFailureDelayMs_lowest) {
                        loginFailureDelayMs = loginFailureDelayMs_lowest;
                        logger.debug("Login failure delay reset");
                    }

                    response.setContentType("application/json");
                    response.setStatus(200);
                    response.getWriter().write("{\"result\":\"OK\", \"isAdmin\":" + isAdmin + "}");
                }
            };
        }

        private AuthenticationFailureHandler failureHandler() {
            return new AuthenticationFailureHandler() {
                @Override
                public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException e) throws IOException, ServletException {
                    logger.warn("Login failure ({})", request.getParameter("username"));
                    _emailService.sendSimpleMessage("BrewPilot login failure", request.getParameter("username"));

                    // Apply an increasing delay to failed login attempts.
                    Utils.sleep(loginFailureDelayMs);
                    if (loginFailureDelayMs < loginFailureDelayMs_highest) {
                        loginFailureDelayMs *= 2;
                        logger.warn("Login failure delay increased to {}", loginFailureDelayMs);
                    }

                    response.setContentType("application/json");
                    response.setStatus(401);
                    response.getWriter().write("{\"result\":\"UNAUTHORIZED\",\"message\":\"Authentication failure\"}");
                }
            };
        }
    }

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    private static abstract class MyWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
        protected void configure(HttpSecurity http, String antPattern, String role) throws Exception {
            // @formatter:off
            http
                .antMatcher(antPattern)
                    .authorizeRequests()
                    .anyRequest().hasRole(role)
                .and().exceptionHandling()
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
                .and().csrf().csrfTokenRepository(csrfTokenRepository()).and().addFilterAfter(csrfHeaderFilter(), CsrfFilter.class)
            ;
            // @formatter:on
        }

        protected AuthenticationEntryPoint authenticationEntryPoint() {
            return new AuthenticationEntryPoint() {
                @Override
                public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException e) throws IOException, ServletException {
                    response.setContentType("application/json");
                    response.setStatus(401);
                    response.getWriter().write("{\"result\":\"UNAUTHORIZED\",\"message\":\"Not authenticated\"}");
                }
            };
        }

        protected AccessDeniedHandler accessDeniedHandler() {
            return new AccessDeniedHandler() {

                @Override
                public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
                        throws IOException, ServletException {
                    response.setContentType("application/json");
                    response.setStatus(403);
                    response.getWriter().write("{\"result\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                }
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
}
