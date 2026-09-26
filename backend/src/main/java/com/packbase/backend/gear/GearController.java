package com.packbase.backend.gear;

import com.packbase.backend.api.GearApi;
import com.packbase.backend.api.model.GearItem;
import com.packbase.backend.api.model.GearItemInput;
import com.packbase.backend.auth.CurrentUser;
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
public class GearController implements GearApi {

	private final GearService service;

	public GearController(GearService service) {
		this.service = service;
	}

	@Override
	public ResponseEntity<List<GearItem>> listGear() {
		return ResponseEntity.ok(service.list(CurrentUser.id()).stream().map(GearController::toDto).toList());
	}

	@Override
	public ResponseEntity<GearItem> createGear(GearItemInput gearItemInput) {
		GearItem created = toDto(service.create(CurrentUser.id(), gearItemInput));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.getId()).toUri();
		return ResponseEntity.status(HttpStatus.CREATED).location(location).body(created);
	}

	@Override
	public ResponseEntity<GearItem> getGearById(UUID id) {
		return ResponseEntity.ok(toDto(service.get(CurrentUser.id(), id)));
	}

	@Override
	public ResponseEntity<GearItem> updateGear(UUID id, GearItemInput gearItemInput) {
		return ResponseEntity.ok(toDto(service.update(CurrentUser.id(), id, gearItemInput)));
	}

	@Override
	public ResponseEntity<Void> deleteGear(UUID id) {
		service.delete(CurrentUser.id(), id);
		return ResponseEntity.noContent().build();
	}

	private static GearItem toDto(GearItemEntity entity) {
		return new GearItem(
			entity.getName(),
			entity.getId(),
			entity.getCreatedAt().atOffset(ZoneOffset.UTC)
		)
		.category(entity.getCategory())
		.weightGrams(entity.getWeightGrams())
		.quantity(entity.getQuantity())
		.notes(entity.getNotes());
	}
}
