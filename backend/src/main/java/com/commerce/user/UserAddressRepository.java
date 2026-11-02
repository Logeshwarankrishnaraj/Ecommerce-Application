package com.commerce.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);

    /** Ownership-scoped lookup: another user's id simply behaves as "not found". */
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
