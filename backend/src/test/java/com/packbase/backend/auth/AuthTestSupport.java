package com.packbase.backend.auth;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for integration tests that need a real registered user and session. */
public final class AuthTestSupport {

	public static final String PASSWORD = "correct horse battery";

	private AuthTestSupport() {
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	public static void register(MockMvc mvc, String email, String password) throws Exception {
		mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isCreated());
	}

	/** Registers a fresh user, logs in, and returns the authenticated session. */
	public static MockHttpSession registerAndLogin(MockMvc mvc) throws Exception {
		String email = uniqueEmail();
		register(mvc, email, PASSWORD);
		return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
	}
}
