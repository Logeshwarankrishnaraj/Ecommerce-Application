package com.commerce.security;

import com.commerce.common.web.ClientIp;
import com.commerce.user.UserDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Primes the XSRF-TOKEN cookie (issued by the CSRF filter on any request). */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/registration/request-otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpChallengeResponse requestOtp(@Valid @RequestBody RegisterOtpRequest body, HttpServletRequest req) {
        return auth.requestRegistrationOtp(body, ClientIp.of(req));
    }

    @PostMapping("/registration/resend-otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpChallengeResponse resend(@Valid @RequestBody ResendOtpRequest body, HttpServletRequest req) {
        return auth.resendRegistrationOtp(body.challengeId(), ClientIp.of(req));
    }

    @PostMapping("/registration/verify-and-complete")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountMe verify(@Valid @RequestBody VerifyRegistrationRequest body, HttpServletRequest req, HttpServletResponse res) {
        return auth.verifyAndComplete(body, ClientIp.of(req), req, res);
    }

    @PostMapping("/login")
    public AccountMe login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        return auth.login(body, ClientIp.of(req), req, res);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resetRequest(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest req) {
        return auth.requestPasswordReset(body, ClientIp.of(req));
    }

    @PostMapping("/password-reset/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetComplete(@Valid @RequestBody PasswordResetComplete body, HttpServletRequest req) {
        auth.completePasswordReset(body, ClientIp.of(req));
    }
}
