package com.handsoap.repository;

import com.handsoap.entity.Oil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OilRepository extends JpaRepository<Oil, Long> {
    Page<Oil> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT o FROM Oil o WHERE o.isActive = true AND " +
            "LOWER(o.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Oil> searchActiveOilsByName(@Param("keyword") String keyword, Pageable pageable);
}
