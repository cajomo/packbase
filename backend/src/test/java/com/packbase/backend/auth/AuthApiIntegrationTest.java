package com.packbase.backend.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static com.packbase.backend.auth.AuthTestSupport.PASSWORD;
import static com.packbase.backend.auth.AuthTestSupport.register;
import static com.packbase.backend.auth.AuthTestSupport.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	UserRepository users;

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	@Test
	void registerLoginMeLogoutLifecycle() throws Exception {
		String email = uniqueEmail();
		register(mvc, email, PASSWORD);

		MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andReturn().getRequest().getSession(false);

		mvc.perform(get("/api/v1/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.id").exists());

		mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void passwordIsStoredHashedNotInPlaintext() throws Exception {
		String email = uniqueEmail();
		register(mvc, email, PASSWORD);

		String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
		assertThat(hash).startsWith("{argon2}").doesNotContain(PASSWORD);
	}

	@Test
	void emailIsCaseInsensitiveAndDuplicatesAreRejected() throws Exception {
		String email = uniqueEmail();
		register(mvc, email, PASSWORD);

		mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email.toUpperCase(), PASSWORD)))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email.toUpperCase(), PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameResponse() throws Exception {
		String email = uniqueEmail();
		register(mvc, email, PASSWORD);

		String wrongPassword = mvc.perform(post("/api/v1/auth/login").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(credentials(email, "not the password")))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		String unknownEmail = mvc.perform(post("/api/v1/auth/login").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(credentials(uniqueEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(wrongPassword).isEqualTo(unknownEmail);
	}

	@Test
	void registrationValidatesInput() throws Exception {
		mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(credentials(uniqueEmail(), "short")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(credentials("not-an-email", PASSWORD)))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(credentials(uniqueEmail(), "x".repeat(129))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginChangesSessionIdToPreventFixation() throws Exception {
		String email = uniqueEmail();
		register(mvc, email, PASSWORD);

		MockHttpSession anonymous = new MockHttpSession();
		String before = anonymous.getId();
		mvc.perform(post("/api/v1/auth/login").session(anonymous).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
				.andExpect(status().isOk());

		assertThat(anonymous.getId()).isNotEqualTo(before);
	}

	@Test
	void postWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(credentials(uniqueEmail(), PASSWORD)))
				.andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestsDoNotCreateSessions() throws Exception {
		assertThat(mvc.perform(get("/api/v1/gear")).andExpect(status().isUnauthorized())
				.andReturn().getRequest().getSession(false)).isNull();
		assertThat(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(credentials(uniqueEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andReturn().getRequest().getSession(false)).isNull();
	}

	@Test
	void csrfCookieIsSetAndReadableByTheSpa() throws Exception {
		mvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(cookie().exists("XSRF-TOKEN"))
				.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}

}
