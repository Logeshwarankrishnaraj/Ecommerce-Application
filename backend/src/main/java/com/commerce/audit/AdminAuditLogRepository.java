package com.commerce.audit;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query("select a from AdminAuditLog a where (:action is null or a.action = :action) and (:actor is null or lower(a.actor) like :actor escape '!')")
    Page<AdminAuditLog> search(@Param("action") String action, @Param("actor") String actor, Pageable pageable);

    @Modifying
    @Query("delete from AdminAuditLog a where a.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from AdminAuditLog a")
    int deleteEverything();
}
