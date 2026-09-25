package com.packbase.backend.gear;

import com.packbase.backend.api.model.GearItemInput;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GearService {

	private final GearItemRepository repository;

	public GearService(GearItemRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<GearItemEntity> list() {
		return repository.findAll(Sort.by("createdAt"));
	}

	@Transactional(readOnly = true)
	public GearItemEntity get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new GearItemNotFoundException(id));
	}

	public GearItemEntity create(GearItemInput input) {
		GearItemEntity entity = new GearItemEntity();
		apply(entity, input);
		return repository.save(entity);
	}

	public GearItemEntity update(UUID id, GearItemInput input) {
		GearItemEntity entity = get(id);
		apply(entity, input);
		return entity;
	}

	public void delete(UUID id) {
		repository.delete(get(id));
	}

	private static void apply(GearItemEntity entity, GearItemInput input) {
		entity.setName(input.getName());
		entity.setCategory(input.getCategory());
		entity.setWeightGrams(input.getWeightGrams());
		entity.setQuantity(input.getQuantity() != null ? input.getQuantity() : 1);
		entity.setNotes(input.getNotes());
	}
}
