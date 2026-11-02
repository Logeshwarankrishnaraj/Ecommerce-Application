package com.commerce.security;

import com.commerce.common.exception.*;
import com.commerce.common.web.RateLimiter;
import com.commerce.config.AppProperties;
import com.commerce.notification.MailService;
import com.commerce.user.*;
import com.commerce.user.UserDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration with email OTP, login with lockout, and password reset.
 * Methods that count failed attempts use noRollbackFor so the counter commits even though an error is returned.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final String GENERIC_LOGIN_FAILURE = "Invalid username/email or password.";
    static final String GENERIC_CODE_FAILURE = "The code is invalid or has expired.";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RegistrationChallengeRepository regChallenges;
    private final PasswordResetChallengeRepository resetChallenges;
    private final PasswordEncoder encoder;
    private final OtpGenerator otp;
    private final MailService mail;
    private final RateLimiter limiter;
    private final SessionAuthenticator sessions;
    private final AppProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AppUserRepository users, RoleRepository roles, RegistrationChallengeRepository regChallenges,
                       PasswordResetChallengeRepository resetChallenges, PasswordEncoder encoder, OtpGenerator otp,
                       MailService mail, RateLimiter limiter, SessionAuthenticator sessions, AppProperties props, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.regChallenges = regChallenges;
        this.resetChallenges = resetChallenges;
        this.encoder = encoder;
        this.otp = otp;
        this.mail = mail;
        this.limiter = limiter;
        this.sessions = sessions;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static String normMobile(String s) {
        return s == null ? null : s.replaceAll("[\\s-]", "");
    }

    // ---------------------------------------------------------------- registration

    @Transactional
    public OtpChallengeResponse requestRegistrationOtp(RegisterOtpRequest req, String ip) {
        limiter.check("reg-ip:" + ip, 10, 900);
        String email = norm(req.email());
        String username = norm(req.username());
        limiter.check("reg-email:" + email, 5, 900);
        if (users.existsByUsername(username)) throw new DuplicateResourceException("username", "That username is already taken.");
        if (users.existsByEmail(email)) throw new DuplicateResourceException("email", "An account with this email already exists.");

        regChallenges.supersedePending(email, username);
        String code = otp.sixDigits();
        Instant now = clock.instant();
        RegistrationChallenge c = new RegistrationChallenge();
        c.setFullName(req.fullName().trim());
        c.setUsername(username);
        c.setEmail(email);
        c.setMobileNumber(normMobile(req.mobileNumber()));
        c.setOtpHash(encoder.encode(code));
        c.setExpiresAt(now.plusSeconds(props.getOtp().getTtlSeconds()));
        c.setLastSentAt(now);
        c.setCreatedAt(now);
        c = regChallenges.save(c);
        sendRegistrationOtp(email, code);   // failure -> EmailDeliveryException -> whole transaction rolls back
        return new OtpChallengeResponse(c.getId(), props.getOtp().getTtlSeconds(), props.getOtp().getResendCooldownSeconds());
    }

    @Transactional
    public OtpChallengeResponse resendRegistrationOtp(Long challengeId, String ip) {
        limiter.check("reg-ip:" + ip, 10, 900);
        RegistrationChallenge c = regChallenges.findById(challengeId).orElseThrow(() -> new BusinessRuleException(GENERIC_CODE_FAILURE));
        Instant now = clock.instant();
        if (!RegistrationChallenge.PENDING.equals(c.getStatus())) throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        if (c.getResendCount() >= props.getOtp().getMaxResends()) throw new RateLimitedException();
        if (c.getLastSentAt().plusSeconds(props.getOtp().getResendCooldownSeconds()).isAfter(now)) throw new RateLimitedException();
        String code = otp.sixDigits();
        c.setOtpHash(encoder.encode(code));
        c.setExpiresAt(now.plusSeconds(props.getOtp().getTtlSeconds()));
        c.setLastSentAt(now);
        c.setResendCount(c.getResendCount() + 1);
        c.setAttempts(0);
        sendRegistrationOtp(c.getEmail(), code);
        return new OtpChallengeResponse(c.getId(), props.getOtp().getTtlSeconds(), props.getOtp().getResendCooldownSeconds());
    }

    private void sendRegistrationOtp(String email, String code) {
        mail.send(email, "Your Jewel Store verification code",
                "Your verification code is " + code + ".\nIt expires in " + (props.getOtp().getTtlSeconds() / 60)
                        + " minutes. If you did not request this, you can ignore this email.");
    }

    @Transactional(noRollbackFor = AppException.class)
    public AccountMe verifyAndComplete(VerifyRegistrationRequest req, String ip, HttpServletRequest http, HttpServletResponse res) {
        limiter.check("reg-verify-ip:" + ip, 20, 900);
        PasswordPolicy.require("password", req.password());
        RegistrationChallenge c = regChallenges.findById(req.challengeId()).orElseThrow(() -> new BusinessRuleException(GENERIC_CODE_FAILURE));
        Instant now = clock.instant();
        if (!RegistrationChallenge.PENDING.equals(c.getStatus())) throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        if (c.getExpiresAt().isBefore(now)) {
            c.setStatus(RegistrationChallenge.EXPIRED);
            throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        }
        if (c.getAttempts() >= props.getOtp().getMaxAttempts()) {
            c.setStatus(RegistrationChallenge.LOCKED);
            throw new RateLimitedException();
        }
        if (!encoder.matches(req.otp(), c.getOtpHash())) {
            c.setAttempts(c.getAttempts() + 1);
            if (c.getAttempts() >= props.getOtp().getMaxAttempts()) c.setStatus(RegistrationChallenge.LOCKED);
            throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        }
        if (users.existsByUsername(c.getUsername()) || users.existsByEmail(c.getEmail())) {
            c.setStatus(RegistrationChallenge.SUPERSEDED);
            throw new DuplicateResourceException("That username or email was registered in the meantime.");
        }
        c.setStatus(RegistrationChallenge.COMPLETED);
        AppUser u = new AppUser();
        u.setFullName(c.getFullName());
        u.setUsername(c.getUsername());
        u.setEmail(c.getEmail());
        u.setMobileNumber(c.getMobileNumber());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setEmailVerifiedAt(now);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        u.getRoles().add(roles.findByRoleCode(Role.CUSTOMER).orElseThrow());   // never client-supplied
        u = users.save(u);
        sessions.start(u, http, res);   // documented policy: successful registration signs the customer in
        return AccountMe.of(u);
    }

    // ---------------------------------------------------------------- login

    @Transactional(noRollbackFor = AppException.class)
    public AccountMe login(LoginRequest req, String ip, HttpServletRequest http, HttpServletResponse res) {
        String key = norm(req.usernameOrEmail());
        limiter.check("login-ip:" + ip, 30, 300);
        limiter.check("login-id:" + key, 10, 900);
        Instant now = clock.instant();
        AppUser u = users.findByUsernameOrEmail(key).orElse(null);
        if (u == null) {
            encoder.matches(req.password(), dummyHash);          // equalize timing
            log.info("Login failed: no account matches '{}'", key);
            throw new UnauthenticatedException(GENERIC_LOGIN_FAILURE);
        }
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
        boolean ok = encoder.matches(req.password(), u.getPasswordHash());
        if (locked || !u.isActive() || !ok) {
            if (!locked && u.isActive() && !ok) {
                u.setFailedLoginCount(u.getFailedLoginCount() + 1);
                if (u.getFailedLoginCount() >= MAX_FAILED_LOGINS) {
                    u.setLockedUntil(now.plus(LOCK_DURATION));
                    u.setFailedLoginCount(0);
                    log.warn("Account locked after repeated failures userId={}", u.getId());
                }
            }
            log.info("Login failed for userId={} (locked={}, active={}, passwordOk={})", u.getId(), locked, u.isActive(), ok);
            throw new UnauthenticatedException(GENERIC_LOGIN_FAILURE);
        }
        log.info("Login OK userId={} roles={}", u.getId(), u.roleCodes());
        u.setFailedLoginCount(0);
        u.setLockedUntil(null);
        sessions.start(u, http, res);
        return AccountMe.of(u);
    }

    // ---------------------------------------------------------------- password reset

    @Transactional
    public MessageResponse requestPasswordReset(PasswordResetRequest req, String ip) {
        limiter.check("reset-ip:" + ip, 10, 900);
        String email = norm(req.email());
        limiter.check("reset-email:" + email, 3, 900);
        MessageResponse generic = new MessageResponse("If an account exists for that email, a reset code has been sent.");
        AppUser u = users.findByEmail(email).orElse(null);
        if (u == null || !u.isActive()) return generic;
        Instant now = clock.instant();
        resetChallenges.consumeAll(u.getId(), now);
        String code = otp.sixDigits();
        PasswordResetChallenge c = new PasswordResetChallenge();
        c.setUserId(u.getId());
        c.setCodeHash(encoder.encode(code));
        c.setExpiresAt(now.plus(Duration.ofMinutes(15)));
        c.setCreatedAt(now);
        resetChallenges.save(c);
        try {
            mail.send(u.getEmail(), "Your Jewel Store password reset code",
                    "Your password reset code is " + code + ". It expires in 15 minutes. If you did not request it, ignore this email.");
        } catch (EmailDeliveryException ex) {
            log.warn("Password reset email could not be sent (response stays generic)");   // never reveal account existence
        }
        return generic;
    }

    @Transactional(noRollbackFor = AppException.class)
    public void completePasswordReset(PasswordResetComplete req, String ip) {
        limiter.check("reset-complete-ip:" + ip, 20, 900);
        PasswordPolicy.require("newPassword", req.newPassword());
        AppUser u = users.findByEmail(norm(req.email())).orElse(null);
        if (u == null) {
            encoder.matches(req.code(), dummyHash);
            throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        }
        Instant now = clock.instant();
        PasswordResetChallenge c = resetChallenges.findOpenByUser(u.getId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (c == null || c.getExpiresAt().isBefore(now) || c.getAttempts() >= props.getOtp().getMaxAttempts()) {
            encoder.matches(req.code(), dummyHash);
            throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        }
        if (!encoder.matches(req.code(), c.getCodeHash())) {
            c.setAttempts(c.getAttempts() + 1);
            throw new BusinessRuleException(GENERIC_CODE_FAILURE);
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setFailedLoginCount(0);
        u.setLockedUntil(null);
        u.setUpdatedAt(now);
        resetChallenges.consumeAll(u.getId(), now);
    }
}
