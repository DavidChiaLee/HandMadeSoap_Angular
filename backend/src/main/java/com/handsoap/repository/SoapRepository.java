package com.handsoap.repository;

import com.handsoap.entity.Soap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SoapRepository extends JpaRepository<Soap, Long> {
    Page<Soap> findByCategoryIdAndIsActiveTrue(Long categoryId, Pageable pageable);

    Page<Soap> findByIsActiveTrue(Pageable pageable);

    Page<Soap> findByIsFeaturedTrueAndIsActiveTrue(Pageable pageable);

    @Query("SELECT s FROM Soap s WHERE s.isActive = true AND (" +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.fullDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Soap> searchActiveSoaps(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT s FROM Soap s WHERE s.isActive = true AND " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Soap> searchActiveSoapsByName(@Param("keyword") String keyword, Pageable pageable);

    List<Soap> findByCategoryId(Long categoryId);
}
