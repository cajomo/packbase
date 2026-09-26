package com.packbase.backend.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PackbaseUserDetailsService implements UserDetailsService {

	private final UserRepository repository;

	public PackbaseUserDetailsService(UserRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return repository.findByEmail(UserService.normalizeEmail(email))
				.map(PackbaseUser::new)
				.orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
	}
}
