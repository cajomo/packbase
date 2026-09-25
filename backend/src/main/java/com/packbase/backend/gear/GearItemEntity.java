package com.packbase.backend.gear;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gear_item")
public class GearItemEntity {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 100)
	private String category;

	private Integer weightGrams;

	@Column(nullable = false)
	private int quantity = 1;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Integer getWeightGrams() {
		return weightGrams;
	}

	public void setWeightGrams(Integer weightGrams) {
		this.weightGrams = weightGrams;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
