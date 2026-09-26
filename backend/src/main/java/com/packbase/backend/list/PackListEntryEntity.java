package com.packbase.backend.list;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pack_list_entry")
public class PackListEntryEntity {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID listId;

	@Column(nullable = false)
	private UUID itemId;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private int quantity;

	/** null = use the item's own category, "" = uncategorized. */
	@Column(length = 100)
	private String category;

	protected PackListEntryEntity() {
	}

	public PackListEntryEntity(UUID listId, UUID itemId, int position, int quantity, String category) {
		this.listId = listId;
		this.itemId = itemId;
		this.position = position;
		this.quantity = quantity;
		this.category = category;
	}

	public UUID getListId() {
		return listId;
	}

	public UUID getItemId() {
		return itemId;
	}

	public int getPosition() {
		return position;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getCategory() {
		return category;
	}
}
