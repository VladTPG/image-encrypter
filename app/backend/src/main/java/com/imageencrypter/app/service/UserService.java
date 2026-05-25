package com.imageencrypter.app.service;

import com.imageencrypter.app.model.Role;
import com.imageencrypter.app.model.User;
import com.imageencrypter.app.repository.UserRepository;
import com.imageencrypter.app.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public record AuthResponse(String token, String role, String email) {}

    public AuthResponse register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        // First user becomes admin
        Role role = userRepository.count() == 0 ? Role.ADMIN : Role.USER;

        User user = new User(email, passwordEncoder.encode(password), role);
        userRepository.save(user);

        String token = jwtUtil.generateToken(email, role);
        return new AuthResponse(token, role.name(), email);
    }

    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getRole().name(), user.getEmail());
    }
}
