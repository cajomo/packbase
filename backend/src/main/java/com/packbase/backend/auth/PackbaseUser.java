package com.packbase.backend.auth;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import java.util.UUID;

/** The authenticated principal: Spring's {@link User} plus the database id. */
public class PackbaseUser extends User {

	private static final long serialVersionUID = 1L;

	private final UUID id;

	public PackbaseUser(UserEntity entity) {
		this(entity.getId(), entity.getEmail(), entity.getPasswordHash());
	}

	public PackbaseUser(UUID id, String email, String passwordHash) {
		super(email, passwordHash, AuthorityUtils.createAuthorityList("ROLE_USER"));
		this.id = id;
	}

	public UUID getId() {
		return id;
	}
}
