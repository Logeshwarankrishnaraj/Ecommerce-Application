package com.commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.commerce.common.exception.*;
import com.commerce.common.web.RateLimiter;
import com.commerce.config.AppProperties;
import com.commerce.notification.MailService;
import com.commerce.user.*;
import com.commerce.user.UserDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** AUTH-01..09 unit-level coverage of the OTP / login rules (no Spring context needed). */
class AuthServiceTest {
    AppUserRepository users = mock(AppUserRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    RegistrationChallengeRepository reg = mock(RegistrationChallengeRepository.class);
    PasswordResetChallengeRepository reset = mock(PasswordResetChallengeRepository.class);
    MailService mail = mock(MailService.class);
    SessionAuthenticator sessions = mock(SessionAuthenticator.class);
    HttpServletRequest http = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    Clock clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);
    AuthService service;

    @BeforeEach
    void setUp() {
        Role customer = new Role();
        customer.setRoleCode(Role.CUSTOMER);
        when(roles.findByRoleCode(Role.CUSTOMER)).thenReturn(Optional.of(customer));
        when(reg.save(any())).thenAnswer(i -> {
            RegistrationChallenge c = i.getArgument(0);
            c.setId(7L);
            return c;
        });
        when(users.save(any())).thenAnswer(i -> {
            AppUser u = i.getArgument(0);
            u.setId(1L);
            return u;
        });
        service = new AuthService(users, roles, reg, reset, encoder, new OtpGenerator(), mail, new RateLimiter(clock),
                sessions, new AppProperties(), clock);
    }

    private String requestOtp() {
        service.requestRegistrationOtp(new RegisterOtpRequest("Asha Rao", "Asha_R", "Asha@Example.com", "9876543210"), "1.1.1.1");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq("asha@example.com"), anyString(), body.capture());
        return body.getValue().replaceAll("(?s).*code is (\\d{6}).*", "$1");
    }

    private RegistrationChallenge savedChallenge() {
        ArgumentCaptor<RegistrationChallenge> cap = ArgumentCaptor.forClass(RegistrationChallenge.class);
        verify(reg).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void auth01_requestStoresPendingChallengeWithHashedOtpAndNoUser() {
        String code = requestOtp();
        RegistrationChallenge c = savedChallenge();
        assertThat(c.getStatus()).isEqualTo(RegistrationChallenge.PENDING);
        assertThat(c.getOtpHash()).isNotEqualTo(code).startsWith("$2");
        assertThat(c.getEmail()).isEqualTo("asha@example.com");
        verify(users, never()).save(any());
    }

    @Test
    void auth02_duplicateEmailIsConflict() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.requestRegistrationOtp(new RegisterOtpRequest("Asha Rao", "asha_r", "asha@example.com", "9876543210"), "ip"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(reg, never()).save(any());
    }

    @Test
    void auth03_wrongOtpIncrementsAttemptsAndDoesNotCreateUser() {
        requestOtp();
        RegistrationChallenge c = savedChallenge();
        when(reg.findById(7L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.verifyAndComplete(new VerifyRegistrationRequest(7L, "000000", "Str0ng!Passw0rd"), "ip", http, res))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(c.getAttempts()).isEqualTo(1);
        verify(users, never()).save(any());
    }

    @Test
    void auth04_otpCannotBeReusedAndExpires() {
        String code = requestOtp();
        RegistrationChallenge c = savedChallenge();
        when(reg.findById(7L)).thenReturn(Optional.of(c));
        service.verifyAndComplete(new VerifyRegistrationRequest(7L, code, "Str0ng!Passw0rd"), "ip", http, res);
        assertThat(c.getStatus()).isEqualTo(RegistrationChallenge.COMPLETED);
        assertThatThrownBy(() -> service.verifyAndComplete(new VerifyRegistrationRequest(7L, code, "Str0ng!Passw0rd"), "ip", http, res))
                .isInstanceOf(BusinessRuleException.class);

        RegistrationChallenge old = new RegistrationChallenge();
        old.setId(8L);
        old.setOtpHash(encoder.encode("123456"));
        old.setExpiresAt(Instant.parse("2026-10-17T09:00:00Z"));
        when(reg.findById(8L)).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> service.verifyAndComplete(new VerifyRegistrationRequest(8L, "123456", "Str0ng!Passw0rd"), "ip", http, res))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(old.getStatus()).isEqualTo(RegistrationChallenge.EXPIRED);
    }

    @Test
    void auth05_attemptLimitLocksChallenge() {
        requestOtp();
        RegistrationChallenge c = savedChallenge();
        when(reg.findById(7L)).thenReturn(Optional.of(c));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verifyAndComplete(new VerifyRegistrationRequest(7L, "000000", "Str0ng!Passw0rd"), "ip" + System.nanoTime(), http, res))
                    .isInstanceOf(AppException.class);
        }
        assertThat(c.getStatus()).isEqualTo(RegistrationChallenge.LOCKED);
    }

    @Test
    void auth06_successCreatesOneCustomerWithHashedPasswordAndStartsSession() {
        String code = requestOtp();
        RegistrationChallenge c = savedChallenge();
        when(reg.findById(7L)).thenReturn(Optional.of(c));
        AccountMe me = service.verifyAndComplete(new VerifyRegistrationRequest(7L, code, "Str0ng!Passw0rd"), "ip", http, res);
        ArgumentCaptor<AppUser> cap = ArgumentCaptor.forClass(AppUser.class);
        verify(users, times(1)).save(cap.capture());
        AppUser u = cap.getValue();
        assertThat(u.getPasswordHash()).isNotEqualTo("Str0ng!Passw0rd");
        assertThat(encoder.matches("Str0ng!Passw0rd", u.getPasswordHash())).isTrue();
        assertThat(u.roleCodes()).containsExactly(Role.CUSTOMER);
        assertThat(u.getEmailVerifiedAt()).isNotNull();
        assertThat(me.roles()).containsExactly(Role.CUSTOMER);
        verify(sessions).start(eq(u), any(), any());
    }

    @Test
    void weakPasswordRejectedBeforeConsumingOtp() {
        requestOtp();
        RegistrationChallenge c = savedChallenge();
        lenient().when(reg.findById(7L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.verifyAndComplete(new VerifyRegistrationRequest(7L, "123456", "weak"), "ip", http, res))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(c.getAttempts()).isZero();
    }

    private AppUser activeUser() {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setUsername("asha");
        u.setEmail("asha@example.com");
        u.setPasswordHash(encoder.encode("Str0ng!Passw0rd"));
        return u;
    }

    @Test
    void auth07_08_loginByUsernameOrEmail() {
        AppUser u = activeUser();
        when(users.findByUsernameOrEmail("asha")).thenReturn(Optional.of(u));
        when(users.findByUsernameOrEmail("asha@example.com")).thenReturn(Optional.of(u));
        assertThat(service.login(new LoginRequest("asha", "Str0ng!Passw0rd"), "ip", http, res)).isNotNull();
        assertThat(service.login(new LoginRequest("ASHA@example.com", "Str0ng!Passw0rd"), "ip", http, res)).isNotNull();
        verify(sessions, times(2)).start(eq(u), any(), any());
    }

    @Test
    void auth09_wrongPasswordDisabledAndUnknownGiveSameGenericError() {
        AppUser u = activeUser();
        AppUser disabled = activeUser();
        disabled.setStatus(AppUser.DISABLED);
        when(users.findByUsernameOrEmail("asha")).thenReturn(Optional.of(u));
        when(users.findByUsernameOrEmail("off")).thenReturn(Optional.of(disabled));
        when(users.findByUsernameOrEmail("ghost")).thenReturn(Optional.empty());
        String m1 = catchMessage(() -> service.login(new LoginRequest("asha", "wrong"), "ip", http, res));
        String m2 = catchMessage(() -> service.login(new LoginRequest("off", "Str0ng!Passw0rd"), "ip", http, res));
        String m3 = catchMessage(() -> service.login(new LoginRequest("ghost", "x"), "ip", http, res));
        assertThat(m1).isEqualTo(m2).isEqualTo(m3).isEqualTo(AuthService.GENERIC_LOGIN_FAILURE);
        verify(sessions, never()).start(any(), any(), any());
    }

    @Test
    void auth05_repeatedLoginFailuresLockTheAccount() {
        AppUser u = activeUser();
        when(users.findByUsernameOrEmail("asha")).thenReturn(Optional.of(u));
        for (int i = 0; i < 5; i++) {
            catchMessage(() -> service.login(new LoginRequest("asha", "wrong"), "ip" + System.nanoTime(), http, res));
        }
        assertThat(u.getLockedUntil()).isAfter(clock.instant());
        // even the right password is refused while locked
        String m = catchMessage(() -> service.login(new LoginRequest("asha", "Str0ng!Passw0rd"), "ipX", http, res));
        assertThat(m).isEqualTo(AuthService.GENERIC_LOGIN_FAILURE);
    }

    @Test
    void auth13_resetRequestIsGenericForUnknownEmail() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        MessageResponse r = service.requestPasswordReset(new PasswordResetRequest("ghost@example.com"), "ip");
        assertThat(r.message()).contains("If an account exists");
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    private String catchMessage(Runnable r) {
        try {
            r.run();
            return "NO_ERROR";
        } catch (AppException e) {
            return e.getMessage();
        }
    }
}
