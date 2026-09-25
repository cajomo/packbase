package com.packbase.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * Temporary: the API is open while there is no real authentication.
	 * Stateless JSON API, so CSRF protection is disabled.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
					.requestMatchers("/api/v1/**").permitAll()
					.anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}
}
