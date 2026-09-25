package com.packbase.backend.gear;

import com.packbase.backend.api.model.GearItemInput;
import com.packbase.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GearController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class GearControllerTest {

	private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Autowired
	MockMvc mvc;

	@MockitoBean
	GearService service;

	private static GearItemEntity entity(String name) {
		GearItemEntity e = new GearItemEntity();
		e.setName(name);
		e.setCategory("Kitchen");
		e.setWeightGrams(12);
		e.setQuantity(2);
		// id and createdAt are normally assigned on persist
		ReflectionTestUtils.setField(e, "id", ID);
		ReflectionTestUtils.setField(e, "createdAt", Instant.parse("2026-01-01T10:00:00Z"));
		return e;
	}

	@Test
	void listReturnsItems() throws Exception {
		when(service.list()).thenReturn(List.of(entity("Spork")));

		mvc.perform(get("/api/v1/gear"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Spork"))
				.andExpect(jsonPath("$[0].id").value(ID.toString()))
				.andExpect(jsonPath("$[0].createdAt").exists());
	}

	@Test
	void createReturns201WithLocation() throws Exception {
		when(service.create(any(GearItemInput.class))).thenReturn(entity("Spork"));

		mvc.perform(post("/api/v1/gear").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Spork\",\"category\":\"Kitchen\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/gear/" + ID))
				.andExpect(jsonPath("$.name").value("Spork"));
	}

	@Test
	void createWithMissingNameReturns400() throws Exception {
		mvc.perform(post("/api/v1/gear").contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\":\"Kitchen\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("name")));
	}

	@Test
	void createWithInvalidQuantityReturns400() throws Exception {
		mvc.perform(post("/api/v1/gear").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Spork\",\"category\":\"Kitchen\",\"quantity\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("quantity")));
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		mvc.perform(post("/api/v1/gear").contentType(MediaType.APPLICATION_JSON).content("{nope"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void getUnknownIdReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.get(id)).thenThrow(new GearItemNotFoundException(id));

		mvc.perform(get("/api/v1/gear/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void getWithInvalidUuidReturns400() throws Exception {
		mvc.perform(get("/api/v1/gear/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void putWithInvalidBodyReturns400() throws Exception {
		mvc.perform(put("/api/v1/gear/" + ID).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\",\"category\":\"Kitchen\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteReturns204() throws Exception {
		mvc.perform(delete("/api/v1/gear/" + ID)).andExpect(status().isNoContent());

		verify(service).delete(ID);
	}
}
