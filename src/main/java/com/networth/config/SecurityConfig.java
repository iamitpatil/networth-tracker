package com.networth.config;

import com.networth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Origins allowed to call the API from a browser. Defaults to the dev servers; production
     * should set {@code CORS_ALLOWED_ORIGINS} to the real front-end hostname.
     */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Authorize the initial request only. Spring Security 6 also filters
                        // ASYNC and ERROR dispatches by default, which breaks SSE: the
                        // security context is cleared when the original request returns, so
                        // the async re-dispatch of a streaming response is seen as anonymous
                        // and rejected. Because the SSE response has already been committed,
                        // the 403 cannot be written either, surfacing as
                        // "Unable to handle the Spring Security Exception because the
                        // response is already committed" instead of a usable error.
                        .shouldFilterAllDispatcherTypes(false)
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/features").permitAll()
                        .requestMatchers("/api/v1/reference-data/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS policy, driven by {@code app.cors.allowed-origins}.
     *
     * <p>This was a hardcoded {@code "*"}. Credentials are not allowed, and the API authenticates
     * with a bearer token rather than a cookie, so a wildcard could not be used to ride a logged-in
     * session -- but it did let any page on the internet call the API and read the response, which
     * matters as soon as a token leaks or an endpoint is made public.
     *
     * <p>{@code setAllowedOriginPatterns} rather than {@code setAllowedOrigins} so that a
     * deployment can use a wildcard subdomain such as {@code https://*.example.com} if it needs to;
     * plain {@code setAllowedOrigins} rejects patterns.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Kept false: the API reads its credential from the Authorization header, so browsers do
        // not need to attach cookies. Leaving it false also keeps a wildcard pattern legal, which
        // the CORS spec forbids once credentials are allowed.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
