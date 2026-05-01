package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints
                    .requestMatchers(HttpMethod.GET, "/{shortCode}").permitAll()
                    .requestMatchers("/ping", "/actuator/health").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll()
                    // Shorten requires auth
                    .requestMatchers("/api/v1/shorten").authenticated()
                    .requestMatchers("/api/v1/stats/**").permitAll()
                    // User URL management requires auth
                    .requestMatchers("/api/v1/urls/**").authenticated()
                    .anyRequest().authenticated()
            )
            .httpBasic(basic -> {}); // Use JWT in production; HTTP Basic for demo

        return http.build();
    }
}
