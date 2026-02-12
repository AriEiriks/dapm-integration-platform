package com.dapm.security_service.config;

import org.springframework.http.HttpMethod;
import com.dapm.security_service.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

// Decides which endpoints are public and which need login.
// Attaches the JWT filter and makes sure sessions are stateless (like an API).
@Configuration
@EnableMethodSecurity  // allows @PreAuthorize etc.
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
			.requestMatchers(
                                "/api/auth/authenticate",
                                "/api/client/pipeline-processingElement/webhook",
                                "/api/peer/**",
                                "/peer/**",
                                "/pipelineBuilder/**",
                                "/pipelineExecution/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/.well-known/jwks.json"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .userDetailsService(customUserDetailsService)
                .build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of(
		"http://dapm1:3000",
        	"http://dapm1.compute.dtu.dk:3000",
                "http://130.225.70.64:3000",   // frontend running on VM1
                "http://130.225.70.65:3000",   // if NAT maps the web UI
                "http://localhost:3000",
                "http://130.225.70.65:*",
                "http://192.168.8.132:*",
                "http://130.225.70.66:*"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // allow cookies/auth headers if needed

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
