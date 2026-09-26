package com.packbase.backend.auth;

import com.packbase.backend.api.AuthApi;
import com.packbase.backend.api.model.LoginRequest;
import com.packbase.backend.api.model.RegisterRequest;
import com.packbase.backend.api.model.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController implements AuthApi {

	private final UserService userService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	public AuthController(UserService userService, AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			HttpServletRequest request, HttpServletResponse response) {
		this.userService = userService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.request = request;
		this.response = response;
	}

	@Override
	public ResponseEntity<UserProfile> register(RegisterRequest registerRequest) {
		UserEntity user = userService.register(registerRequest.getEmail(), registerRequest.getPassword());
		return ResponseEntity.status(HttpStatus.CREATED).body(new UserProfile(user.getId(), user.getEmail()));
	}

	@Override
	public ResponseEntity<UserProfile> login(LoginRequest loginRequest) {
		// throws BadCredentialsException (-> 401) for an unknown email or wrong password
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(
						UserService.normalizeEmail(loginRequest.getEmail()), loginRequest.getPassword()));

		// new session id on login prevents session fixation
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		PackbaseUser user = (PackbaseUser) authentication.getPrincipal();
		return ResponseEntity.ok(new UserProfile(user.getId(), user.getUsername()));
	}

	@Override
	public ResponseEntity<Void> logout() {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<UserProfile> getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		PackbaseUser user = (PackbaseUser) authentication.getPrincipal();
		return ResponseEntity.ok(new UserProfile(user.getId(), user.getUsername()));
	}
}
