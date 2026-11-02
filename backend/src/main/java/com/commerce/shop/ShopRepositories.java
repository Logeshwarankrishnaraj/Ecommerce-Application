package com.commerce.shop;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class ShopRepositories {
    private ShopRepositories() {}

    public interface ShopProfileRepository extends JpaRepository<ShopProfile, Long> {}

    public interface SiteLinkRepository extends JpaRepository<SiteLink, Long> {
        List<SiteLink> findByActiveTrueOrderBySortOrderAscIdAsc();
        List<SiteLink> findAllByOrderByPlacementAscSortOrderAscIdAsc();
    }

    public interface ContentPageRepository extends JpaRepository<ContentPage, Long> {
        Optional<ContentPage> findBySlug(String slug);
        boolean existsBySlug(String slug);
        List<ContentPage> findAllByOrderByIdAsc();
    }

    public interface ContentBlockRepository extends JpaRepository<ContentBlock, Long> {
        List<ContentBlock> findByPageIdOrderBySortOrderAscIdAsc(Long pageId);

        @Modifying
        @Query("delete from ContentBlock b where b.pageId = :pageId")
        void deleteByPageId(@Param("pageId") Long pageId);
    }
}
