package com.commerce.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class OtpGenerator {
    private final SecureRandom random = new SecureRandom();

    /** Six-digit numeric code from a CSPRNG. */
    public String sixDigits() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
