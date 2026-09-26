package com.packbase.backend.gear;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static com.packbase.backend.auth.AuthTestSupport.registerAndLogin;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class GearApiIntegrationTest {

	@Autowired
	MockMvc mvc;

	private String createSpork(MockHttpSession session) throws Exception {
		String body = mvc.perform(post("/api/v1/gear").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Titanium spork\",\"category\":\"Kitchen\",\"weightGrams\":12}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void fullCrudLifecycle() throws Exception {
		MockHttpSession session = registerAndLogin(mvc);

		String body = mvc.perform(post("/api/v1/gear").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Titanium spork\",\"category\":\"Kitchen\",\"weightGrams\":12}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.quantity").value(1))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/v1/gear/" + id).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Titanium spork"));

		mvc.perform(put("/api/v1/gear/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Titanium spork\",\"category\":\"Kitchen\",\"quantity\":3,\"notes\":\"Light\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(3))
				.andExpect(jsonPath("$.notes").value("Light"))
				.andExpect(jsonPath("$.weightGrams").doesNotExist());

		mvc.perform(get("/api/v1/gear").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));

		mvc.perform(delete("/api/v1/gear/" + id).session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/gear/" + id).session(session)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/gear/" + id).session(session).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	void categoryIsOptionalAndDefaultsToEmpty() throws Exception {
		MockHttpSession session = registerAndLogin(mvc);

		mvc.perform(post("/api/v1/gear").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Mystery\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").value(""));
	}

	@Test
	void anonymousAccessIsRejected() throws Exception {
		mvc.perform(get("/api/v1/gear")).andExpect(status().isUnauthorized());
	}

	@Test
	void usersCannotSeeOrModifyEachOthersItems() throws Exception {
		MockHttpSession alice = registerAndLogin(mvc);
		MockHttpSession bob = registerAndLogin(mvc);
		String aliceItem = createSpork(alice);

		mvc.perform(get("/api/v1/gear").session(bob))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/v1/gear/" + aliceItem).session(bob)).andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/gear/" + aliceItem).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Stolen\",\"category\":\"Kitchen\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/gear/" + aliceItem).session(bob).with(csrf())).andExpect(status().isNotFound());

		// still intact for the owner
		mvc.perform(get("/api/v1/gear/" + aliceItem).session(alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Titanium spork"));
	}
}
