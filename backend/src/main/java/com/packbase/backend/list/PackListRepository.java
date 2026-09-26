package com.packbase.backend.list;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackListRepository extends JpaRepository<PackListEntity, UUID> {

	List<PackListEntity> findAllByOwnerId(UUID ownerId, Sort sort);

	Optional<PackListEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
