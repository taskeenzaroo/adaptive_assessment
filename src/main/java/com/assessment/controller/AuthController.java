package com.assessment.controller;

import com.assessment.config.JwtUtil;
import com.assessment.entity.User;
import com.assessment.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }
        User user = new User();
        user.setName(body.get("name"));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(body.get("password")));
        user.setRole(User.Role.valueOf(body.getOrDefault("role", "STUDENT").toUpperCase()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Registered successfully", "email", email));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.get("email"), body.get("password")));
            User user = userRepository.findByEmail(body.get("email")).orElseThrow();
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
            return ResponseEntity.ok(Map.of(
                "token", token, "name", user.getName(),
                "email", user.getEmail(), "role", user.getRole().name(), "id", user.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }
    }
}
