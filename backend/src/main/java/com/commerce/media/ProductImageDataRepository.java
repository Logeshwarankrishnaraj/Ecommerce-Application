package com.commerce.media;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageDataRepository extends JpaRepository<ProductImageData, String> {
    /** Only the requested column is read, so a thumbnail request never pulls the large image. */
    @Query("select d.imageBase64 from ProductImageData d where d.storageKey = :key")
    Optional<String> findLargeBase64(@Param("key") String key);

    @Query("select d.thumbBase64 from ProductImageData d where d.storageKey = :key")
    Optional<String> findThumbBase64(@Param("key") String key);
}
