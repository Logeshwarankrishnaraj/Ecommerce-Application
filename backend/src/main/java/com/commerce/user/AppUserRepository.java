package com.commerce.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select u from AppUser u where u.username = :v or u.email = :v")
    Optional<AppUser> findByUsernameOrEmail(@Param("v") String value);

    @Query("select distinct u from AppUser u left join u.roles r where "
            + "(:q is null or lower(u.username) like :q escape '!' or lower(u.email) like :q escape '!' or lower(u.fullName) like :q escape '!') "
            + "and (:status is null or u.status = :status) and (:role is null or r.roleCode = :role)")
    Page<AppUser> search(@Param("q") String q, @Param("status") String status, @Param("role") String role, Pageable pageable);
}
