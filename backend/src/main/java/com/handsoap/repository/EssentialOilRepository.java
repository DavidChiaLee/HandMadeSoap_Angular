package com.handsoap.repository;

import com.handsoap.entity.EssentialOil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EssentialOilRepository extends JpaRepository<EssentialOil, Long> {
    Page<EssentialOil> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT e FROM EssentialOil e WHERE e.isActive = true AND " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<EssentialOil> searchActiveEssentialOils(@Param("keyword") String keyword, Pageable pageable);
}
