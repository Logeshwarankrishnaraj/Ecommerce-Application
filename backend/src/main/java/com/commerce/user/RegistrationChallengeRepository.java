package com.commerce.user;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationChallengeRepository extends JpaRepository<RegistrationChallenge, Long> {
    @Modifying
    @Query("update RegistrationChallenge c set c.status = 'SUPERSEDED' where c.status = 'PENDING' and (c.email = :email or c.username = :username)")
    int supersedePending(@Param("email") String email, @Param("username") String username);

    @Modifying
    @Query("delete from RegistrationChallenge c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
