package com.packbase.backend.gear;

import com.packbase.backend.api.model.ErrorResponse;
import com.packbase.backend.auth.EmailAlreadyRegisteredException;
import com.packbase.backend.list.InvalidPackListException;
import com.packbase.backend.list.PackListNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(GearItemNotFoundException.class)
	ResponseEntity<ErrorResponse> notFound(GearItemNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(PackListNotFoundException.class)
	ResponseEntity<ErrorResponse> listNotFound(PackListNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(InvalidPackListException.class)
	ResponseEntity<ErrorResponse> invalidList(InvalidPackListException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ResponseEntity<ErrorResponse> emailTaken(EmailAlreadyRegisteredException e) {
		return error(HttpStatus.CONFLICT, e.getMessage());
	}

	/** Deliberately the same message for an unknown email and a wrong password. */
	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ErrorResponse> badCredentials(AuthenticationException e) {
		return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + " " + f.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining("; "));
		return error(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Invalid request payload" : message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
		return error(HttpStatus.BAD_REQUEST, "Malformed request body");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> badArgument(MethodArgumentTypeMismatchException e) {
		return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter " + e.getName());
	}

	private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ErrorResponse(message));
	}
}
