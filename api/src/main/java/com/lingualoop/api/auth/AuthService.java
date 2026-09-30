package com.lingualoop.api.auth;

import com.lingualoop.api.auth.dto.AuthResponse;
import com.lingualoop.api.auth.dto.LearnerDto;
import com.lingualoop.api.auth.dto.LoginRequest;
import com.lingualoop.api.auth.dto.RegisterRequest;
import com.lingualoop.api.common.config.AdminProperties;
import com.lingualoop.api.common.error.BadRequestException;
import com.lingualoop.api.common.error.ConflictException;
import com.lingualoop.api.common.error.UnauthorizedException;
import com.lingualoop.api.learner.Learner;
import com.lingualoop.api.learner.LearnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final LearnerRepository learners;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminProperties adminProperties;

    public AuthService(LearnerRepository learners, PasswordEncoder passwordEncoder, JwtService jwtService,
            AdminProperties adminProperties) {
        this.learners = learners;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminProperties = adminProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (learners.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? "UTC"
                : request.timezone().trim();
        try {
            java.time.ZoneId.of(timezone);
        } catch (java.time.DateTimeException ex) {
            throw new BadRequestException("Invalid timezone: " + timezone);
        }
        Learner learner = new Learner(
                request.email().trim().toLowerCase(),
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                timezone);
        if (adminProperties.emails().contains(learner.getEmail())) {
            learner.setRole(com.lingualoop.api.learner.LearnerRole.ADMIN);
        }
        learner = learners.save(learner);
        return new AuthResponse(jwtService.generateToken(learner), LearnerDto.from(learner));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Learner learner = learners.findByEmailIgnoreCase(request.email().trim())
                .filter(l -> passwordEncoder.matches(request.password(), l.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return new AuthResponse(jwtService.generateToken(learner), LearnerDto.from(learner));
    }
}
