package com.packbase.backend.list;

import com.jayway.jsonpath.JsonPath;
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
class PackListApiIntegrationTest {

	@Autowired
	MockMvc mvc;

	private String createItem(MockHttpSession session, String name) throws Exception {
		String body = mvc.perform(post("/api/v1/gear").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\",\"category\":\"Kitchen\",\"weightGrams\":10}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String createList(MockHttpSession session, String json) throws Exception {
		String body = mvc.perform(post("/api/v1/lists").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String entry(String itemId, int quantity, String categoryJson) {
		return "{\"itemId\":\"" + itemId + "\",\"quantity\":" + quantity
				+ (categoryJson != null ? ",\"category\":" + categoryJson : "") + "}";
	}

	private static String list(String name, String... entries) {
		return "{\"name\":\"" + name + "\",\"entries\":[" + String.join(",", entries) + "]}";
	}

	@Test
	void fullLifecycleKeepsEntryOrderQuantityAndCategoryOverride() throws Exception {
		MockHttpSession session = registerAndLogin(mvc);
		String a = createItem(session, "Spork");
		String b = createItem(session, "Stove");
		String c = createItem(session, "Pot");

		String id = createList(session, list("Kungsleden", entry(a, 1, null), entry(b, 2, "\"Cooking\"")));

		mvc.perform(get("/api/v1/lists/" + id).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Kungsleden"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.entries", hasSize(2)))
				.andExpect(jsonPath("$.entries[0].itemId").value(a))
				.andExpect(jsonPath("$.entries[0].category").doesNotExist())
				.andExpect(jsonPath("$.entries[1].itemId").value(b))
				.andExpect(jsonPath("$.entries[1].quantity").value(2))
				.andExpect(jsonPath("$.entries[1].category").value("Cooking"));

		// reorder (swap) + add + rename + "uncategorized" override in one replace
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Kungsleden 2", entry(b, 2, "\"Cooking\""), entry(c, 1, "\"\""), entry(a, 3, null))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Kungsleden 2"))
				.andExpect(jsonPath("$.entries[0].itemId").value(b))
				.andExpect(jsonPath("$.entries[1].itemId").value(c))
				.andExpect(jsonPath("$.entries[1].category").value(""))
				.andExpect(jsonPath("$.entries[2].itemId").value(a))
				.andExpect(jsonPath("$.entries[2].quantity").value(3));

		mvc.perform(get("/api/v1/lists").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].entries", hasSize(3)))
				.andExpect(jsonPath("$[0].entries[0].itemId").value(b));

		// emptying the list is allowed
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Kungsleden 2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries", hasSize(0)));

		mvc.perform(delete("/api/v1/lists/" + id).session(session).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/lists/" + id).session(session)).andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/lists/" + id).session(session).with(csrf())).andExpect(status().isNotFound());
		// the library item survives deleting the list
		mvc.perform(get("/api/v1/gear/" + a).session(session)).andExpect(status().isOk());
	}

	@Test
	void newestListComesFirst() throws Exception {
		MockHttpSession session = registerAndLogin(mvc);
		createList(session, list("first"));
		createList(session, list("second"));

		mvc.perform(get("/api/v1/lists").session(session))
				.andExpect(jsonPath("$[0].name").value("second"))
				.andExpect(jsonPath("$[1].name").value("first"));
	}

	@Test
	void deletingALibraryItemRemovesItFromLists() throws Exception {
		MockHttpSession session = registerAndLogin(mvc);
		String a = createItem(session, "Spork");
		String b = createItem(session, "Stove");
		String id = createList(session, list("Trip", entry(a, 1, null), entry(b, 1, null)));

		mvc.perform(delete("/api/v1/gear/" + a).session(session).with(csrf())).andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/lists/" + id).session(session))
				.andExpect(jsonPath("$.entries", hasSize(1)))
				.andExpect(jsonPath("$.entries[0].itemId").value(b));
	}

	@Test
	void invalidListsAreRejected() throws Exception {
		MockHttpSession session = registerAndLogin(mvc);
		String a = createItem(session, "Spork");
		String id = createList(session, list("Trip"));

		// duplicate item
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Trip", entry(a, 1, null), entry(a, 2, null))))
				.andExpect(status().isBadRequest());
		// unknown item
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Trip", entry("00000000-0000-0000-0000-000000000000", 1, null))))
				.andExpect(status().isBadRequest());
		// bad quantity, empty name, over-long name
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Trip", entry(a, 0, null))))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("")))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/v1/lists/" + id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("x".repeat(101))))
				.andExpect(status().isBadRequest());

		// a rejected update leaves the list untouched
		mvc.perform(get("/api/v1/lists/" + id).session(session))
				.andExpect(jsonPath("$.name").value("Trip"))
				.andExpect(jsonPath("$.entries", hasSize(0)));
	}

	@Test
	void usersCannotSeeOrTouchEachOthersListsOrItems() throws Exception {
		MockHttpSession alice = registerAndLogin(mvc);
		MockHttpSession bob = registerAndLogin(mvc);
		String aliceItem = createItem(alice, "Spork");
		String aliceList = createList(alice, list("Alice trip", entry(aliceItem, 1, null)));
		String bobList = createList(bob, list("Bob trip"));

		mvc.perform(get("/api/v1/lists").session(bob))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Bob trip"));
		mvc.perform(get("/api/v1/lists/" + aliceList).session(bob)).andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/lists/" + aliceList).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Hijacked")))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/lists/" + aliceList).session(bob).with(csrf())).andExpect(status().isNotFound());

		// Bob cannot put Alice's library item on his own list
		mvc.perform(put("/api/v1/lists/" + bobList).session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Bob trip", entry(aliceItem, 1, null))))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/lists").session(bob).with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(list("Sneaky", entry(aliceItem, 1, null))))
				.andExpect(status().isBadRequest());

		mvc.perform(get("/api/v1/lists/" + aliceList).session(alice))
				.andExpect(jsonPath("$.name").value("Alice trip"))
				.andExpect(jsonPath("$.entries", hasSize(1)));
	}

	@Test
	void anonymousAccessIsRejected() throws Exception {
		mvc.perform(get("/api/v1/lists")).andExpect(status().isUnauthorized());
	}
}
