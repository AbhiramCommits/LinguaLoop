package com.lingualoop.api.auth;

import com.lingualoop.api.auth.dto.AuthResponse;
import com.lingualoop.api.auth.dto.LearnerDto;
import com.lingualoop.api.auth.dto.LoginRequest;
import com.lingualoop.api.auth.dto.RegisterRequest;
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

    public AuthService(LearnerRepository learners, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.learners = learners;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (learners.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        Learner learner = learners.save(new Learner(
                request.email().trim().toLowerCase(),
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                request.timezone()));
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
