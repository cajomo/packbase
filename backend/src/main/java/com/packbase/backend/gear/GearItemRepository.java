package com.packbase.backend.gear;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GearItemRepository extends JpaRepository<GearItemEntity, UUID> {

	List<GearItemEntity> findAllByOwnerId(UUID ownerId, Sort sort);

	Optional<GearItemEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
