package com.packbase.backend.gear;

import com.packbase.backend.api.model.GearItemInput;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Every operation is scoped to an owner. An item that belongs to someone else is
 * indistinguishable from one that does not exist (404), so ids cannot be probed.
 */
@Service
@Transactional
public class GearService {

	private final GearItemRepository repository;

	public GearService(GearItemRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<GearItemEntity> list(UUID ownerId) {
		return repository.findAllByOwnerId(ownerId, Sort.by("createdAt"));
	}

	@Transactional(readOnly = true)
	public GearItemEntity get(UUID ownerId, UUID id) {
		return repository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new GearItemNotFoundException(id));
	}

	public GearItemEntity create(UUID ownerId, GearItemInput input) {
		GearItemEntity entity = new GearItemEntity();
		entity.setOwnerId(ownerId);
		apply(entity, input);
		return repository.save(entity);
	}

	public GearItemEntity update(UUID ownerId, UUID id, GearItemInput input) {
		GearItemEntity entity = get(ownerId, id);
		apply(entity, input);
		return entity;
	}

	public void delete(UUID ownerId, UUID id) {
		repository.delete(get(ownerId, id));
	}

	private static void apply(GearItemEntity entity, GearItemInput input) {
		entity.setName(input.getName());
		entity.setCategory(input.getCategory());
		entity.setWeightGrams(input.getWeightGrams());
		entity.setQuantity(input.getQuantity() != null ? input.getQuantity() : 1);
		entity.setNotes(input.getNotes());
	}
}
