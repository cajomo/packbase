package com.packbase.backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {

	private CurrentUser() {
	}

	/** Id of the logged-in user. Only call from endpoints that require authentication. */
	public static UUID id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof PackbaseUser user)) {
			throw new IllegalStateException("No authenticated user in the security context");
		}
		return user.getId();
	}
}
