package com.packbase.backend.list;

import java.util.UUID;

public class PackListNotFoundException extends RuntimeException {

	public PackListNotFoundException(UUID id) {
		super("Pack list " + id + " not found");
	}
}
