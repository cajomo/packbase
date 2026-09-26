package com.packbase.backend.list;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackListEntryRepository extends JpaRepository<PackListEntryEntity, UUID> {

	List<PackListEntryEntity> findAllByListIdInOrderByPosition(Collection<UUID> listIds);

	/** Executes immediately (and flushes first), so replacement rows can be inserted right after. */
	@Modifying(flushAutomatically = true)
	@Query("delete from PackListEntryEntity e where e.listId = :listId")
	void deleteAllByListId(@Param("listId") UUID listId);
}
