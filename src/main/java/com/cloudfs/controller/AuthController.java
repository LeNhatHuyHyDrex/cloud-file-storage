package com.cloudfs.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.dto.Dtos.AuthResponse;
import com.cloudfs.dto.Dtos.Credentials;
import com.cloudfs.model.AppUser;
import com.cloudfs.repository.UserRepository;
import com.cloudfs.security.JwtService;
import com.cloudfs.service.LoginAttemptService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Đăng ký / đăng nhập, trả về JWT")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final long defaultQuota;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                          LoginAttemptService attempts,
                          @Value("${app.storage.default-quota-bytes}") long defaultQuota) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.defaultQuota = defaultQuota;
    }

    @Operation(summary = "Tạo tài khoản mới")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthResponse register(@Valid @RequestBody Credentials body) {
        if (users.existsByUsername(body.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        users.save(new AppUser(body.username(), encoder.encode(body.password()), defaultQuota));
        return new AuthResponse(jwt.generateToken(body.username()), body.username(), jwt.expirationMs());
    }

    @Operation(summary = "Đăng nhập", description = "Khóa tạm 5 phút sau 5 lần sai liên tiếp")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody Credentials body) {
        if (attempts.isLocked(body.username())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau ít phút");
        }
        AppUser user = users.findByUsername(body.username())
                .filter(u -> encoder.matches(body.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            attempts.failed(body.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu");
        }
        attempts.succeeded(body.username());
        return new AuthResponse(jwt.generateToken(user.getUsername()), user.getUsername(), jwt.expirationMs());
    }
}
