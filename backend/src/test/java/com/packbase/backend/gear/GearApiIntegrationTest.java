package com.packbase.backend.gear;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

	@Test
	void fullCrudLifecycle() throws Exception {
		String body = mvc.perform(post("/api/v1/gear").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Titanium spork\",\"category\":\"Kitchen\",\"weightGrams\":12}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.quantity").value(1))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/v1/gear/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Titanium spork"));

		mvc.perform(put("/api/v1/gear/" + id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Titanium spork\",\"category\":\"Kitchen\",\"quantity\":3,\"notes\":\"Light\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(3))
				.andExpect(jsonPath("$.notes").value("Light"))
				.andExpect(jsonPath("$.weightGrams").doesNotExist());

		mvc.perform(get("/api/v1/gear")).andExpect(status().isOk());

		mvc.perform(delete("/api/v1/gear/" + id)).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/gear/" + id)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/gear/" + id)).andExpect(status().isNotFound());
	}
}
