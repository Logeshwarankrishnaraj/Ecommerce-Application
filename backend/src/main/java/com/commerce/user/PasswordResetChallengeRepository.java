package com.commerce.user;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, Long> {
    @Query("select c from PasswordResetChallenge c where c.userId = :uid and c.consumedAt is null order by c.createdAt desc")
    List<PasswordResetChallenge> findOpenByUser(@Param("uid") Long userId, Pageable pageable);

    @Modifying
    @Query("update PasswordResetChallenge c set c.consumedAt = :now where c.userId = :uid and c.consumedAt is null")
    int consumeAll(@Param("uid") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetChallenge c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
