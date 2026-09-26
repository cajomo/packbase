package com.packbase.backend.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class UserService {

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	/** Emails are stored trimmed and lower-cased so lookups and the unique index are case-insensitive. */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public UserEntity register(String email, String rawPassword) {
		String normalized = normalizeEmail(email);
		if (repository.existsByEmail(normalized)) {
			throw new EmailAlreadyRegisteredException();
		}
		try {
			return repository.saveAndFlush(new UserEntity(normalized, passwordEncoder.encode(rawPassword)));
		} catch (DataIntegrityViolationException e) {
			// lost a race against a concurrent registration of the same email
			throw new EmailAlreadyRegisteredException();
		}
	}
}
