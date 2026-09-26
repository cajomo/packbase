package com.packbase.backend.list;

import com.packbase.backend.api.ListsApi;
import com.packbase.backend.api.model.ListEntry;
import com.packbase.backend.api.model.PackList;
import com.packbase.backend.api.model.PackListInput;
import com.packbase.backend.auth.CurrentUser;
import com.packbase.backend.list.PackListService.PackListView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PackListController implements ListsApi {

	private final PackListService service;

	public PackListController(PackListService service) {
		this.service = service;
	}

	@Override
	public ResponseEntity<List<PackList>> listPackLists() {
		return ResponseEntity.ok(service.list(CurrentUser.id()).stream().map(PackListController::toDto).toList());
	}

	@Override
	public ResponseEntity<PackList> createPackList(PackListInput input) {
		PackList created = toDto(service.create(CurrentUser.id(), input));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.getId()).toUri();
		return ResponseEntity.status(HttpStatus.CREATED).location(location).body(created);
	}

	@Override
	public ResponseEntity<PackList> getPackListById(UUID id) {
		return ResponseEntity.ok(toDto(service.get(CurrentUser.id(), id)));
	}

	@Override
	public ResponseEntity<PackList> updatePackList(UUID id, PackListInput input) {
		return ResponseEntity.ok(toDto(service.update(CurrentUser.id(), id, input)));
	}

	@Override
	public ResponseEntity<Void> deletePackList(UUID id) {
		service.delete(CurrentUser.id(), id);
		return ResponseEntity.noContent().build();
	}

	private static PackList toDto(PackListView view) {
		List<ListEntry> entries = view.entries().stream()
				.map(e -> new ListEntry(e.getItemId(), e.getQuantity()).category(e.getCategory()))
				.toList();
		return new PackList(view.list().getId(), view.list().getName(),
				view.list().getCreatedAt().atOffset(ZoneOffset.UTC), entries);
	}
}
