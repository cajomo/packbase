package com.packbase.backend.gear;

import java.util.UUID;

public class GearItemNotFoundException extends RuntimeException {

	public GearItemNotFoundException(UUID id) {
		super("Gear item not found: " + id);
	}
}
