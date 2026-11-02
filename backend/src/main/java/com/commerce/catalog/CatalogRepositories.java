package com.commerce.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class CatalogRepositories {
    private CatalogRepositories() {}

    public interface CategoryRepository extends JpaRepository<Category, Long> {
        Optional<Category> findBySlug(String slug);

        List<Category> findAllByOrderBySortOrderAscNameAsc();

        List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();

        long countByParentId(Long parentId);
    }

    public interface ProductRepository extends JpaRepository<Product, Long> {
        Optional<Product> findBySlug(String slug);

        Optional<Product> findBySku(String sku);

        boolean existsBySku(String sku);

        boolean existsBySlug(String slug);

        long countByCategoryId(Long categoryId);

        long countByCategoryIdAndStatus(Long categoryId, String status);

        long countByStatusNot(String status);

        @Query("select p from Product p where (:status is null or p.status = :status) and (:cat is null or p.categoryId = :cat) "
                + "and (:q is null or lower(p.title) like :q escape '!' or lower(p.sku) like :q escape '!') order by p.updatedAt desc, p.id desc")
        org.springframework.data.domain.Page<Product> adminSearch(@Param("status") String status, @Param("cat") Long categoryId,
                                                                  @Param("q") String q, org.springframework.data.domain.Pageable pageable);

        @Query("select p from Product p where p.status = 'PUBLISHED' and p.categoryId = :cat and p.id <> :id order by p.createdAt desc, p.id desc")
        List<Product> findRelated(@Param("cat") Long categoryId, @Param("id") Long productId, org.springframework.data.domain.Pageable pageable);
    }

    public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
        List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

        List<ProductImage> findByProductIdInOrderBySortOrderAsc(Collection<Long> productIds);

        Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

        long countByProductId(Long productId);
    }

    public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, ProductAttributeValue.Key> {
        @Query("select v from ProductAttributeValue v where v.id.productId = :pid")
        List<ProductAttributeValue> findByProduct(@Param("pid") Long productId);

        @Modifying
        @Query("delete from ProductAttributeValue v where v.id.productId = :pid")
        void deleteByProduct(@Param("pid") Long productId);
    }
}
