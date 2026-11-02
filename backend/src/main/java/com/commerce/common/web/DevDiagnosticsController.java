package com.commerce.common.web;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** LOCAL/TEST ONLY: proves browser -> proxy -> backend -> MySQL works. Not registered under any other profile. */
@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/dev")
public class DevDiagnosticsController {
    private final EntityManager em;
    private final Environment env;

    public DevDiagnosticsController(EntityManager em, Environment env) {
        this.em = em;
        this.env = env;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Number users = (Number) em.createNativeQuery("select count(*) from app_users").getSingleResult();
        Number roles = (Number) em.createNativeQuery("select count(*) from roles").getSingleResult();
        return Map.of("status", "ok", "database", "reachable", "appUsers", users.longValue(), "roles", roles.longValue(),
                "profiles", env.getActiveProfiles(), "serverTime", Instant.now().toString());
    }
}
