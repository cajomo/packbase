package com.packbase.backend.list;

import com.packbase.backend.api.model.ListEntry;
import com.packbase.backend.api.model.PackListInput;
import com.packbase.backend.gear.GearItemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every operation is scoped to an owner: another user's list looks like a missing one (404),
 * and a list may only reference the caller's own library items.
 */
@Service
@Transactional
public class PackListService {

	public record PackListView(PackListEntity list, List<PackListEntryEntity> entries) {
	}

	private final PackListRepository lists;
	private final PackListEntryRepository entries;
	private final GearItemRepository gear;

	public PackListService(PackListRepository lists, PackListEntryRepository entries, GearItemRepository gear) {
		this.lists = lists;
		this.entries = entries;
		this.gear = gear;
	}

	@Transactional(readOnly = true)
	public List<PackListView> list(UUID ownerId) {
		List<PackListEntity> owned = lists.findAllByOwnerId(ownerId, Sort.by(Sort.Direction.DESC, "createdAt"));
		if (owned.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<PackListEntryEntity>> byList = new LinkedHashMap<>();
		entries.findAllByListIdInOrderByPosition(owned.stream().map(PackListEntity::getId).toList())
				.forEach(e -> byList.computeIfAbsent(e.getListId(), k -> new ArrayList<>()).add(e));
		return owned.stream().map(l -> new PackListView(l, byList.getOrDefault(l.getId(), List.of()))).toList();
	}

	@Transactional(readOnly = true)
	public PackListView get(UUID ownerId, UUID id) {
		PackListEntity list = owned(ownerId, id);
		return new PackListView(list, entries.findAllByListIdInOrderByPosition(List.of(id)));
	}

	public PackListView create(UUID ownerId, PackListInput input) {
		List<ListEntry> requested = requestedEntries(input);
		validate(ownerId, requested);
		PackListEntity list = new PackListEntity();
		list.setOwnerId(ownerId);
		list.setName(input.getName());
		lists.saveAndFlush(list);
		return new PackListView(list, saveEntries(list.getId(), requested));
	}

	public PackListView update(UUID ownerId, UUID id, PackListInput input) {
		List<ListEntry> requested = requestedEntries(input);
		PackListEntity list = owned(ownerId, id);
		validate(ownerId, requested);
		list.setName(input.getName());
		entries.deleteAllByListId(id);
		return new PackListView(list, saveEntries(id, requested));
	}

	public void delete(UUID ownerId, UUID id) {
		lists.delete(owned(ownerId, id));
	}

	private PackListEntity owned(UUID ownerId, UUID id) {
		return lists.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new PackListNotFoundException(id));
	}

	private static List<ListEntry> requestedEntries(PackListInput input) {
		return input.getEntries() != null ? input.getEntries() : List.of();
	}

	private void validate(UUID ownerId, List<ListEntry> requested) {
		Set<UUID> ids = new HashSet<>();
		for (ListEntry entry : requested) {
			if (!ids.add(entry.getItemId())) {
				throw new InvalidPackListException("Item " + entry.getItemId() + " appears more than once");
			}
		}
		if (!ids.isEmpty() && gear.countByOwnerIdAndIdIn(ownerId, ids) != ids.size()) {
			throw new InvalidPackListException("Entries reference unknown items");
		}
	}

	private List<PackListEntryEntity> saveEntries(UUID listId, List<ListEntry> requested) {
		List<PackListEntryEntity> rows = new ArrayList<>(requested.size());
		for (int i = 0; i < requested.size(); i++) {
			ListEntry e = requested.get(i);
			rows.add(new PackListEntryEntity(listId, e.getItemId(), i, e.getQuantity(), e.getCategory()));
		}
		return entries.saveAll(rows);
	}
}
