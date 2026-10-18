package com.handsoap.repository;

import com.handsoap.entity.EssentialOil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EssentialOilRepository extends JpaRepository<EssentialOil, Long> {
    Page<EssentialOil> findByIsActiveTrue(Pageable pageable);

    /**
     * 依名稱關鍵字搜尋；keyword 須經 {@link KeywordSanitizer#normalize(String)} 跳釋（review L3）。
     * 方法名與 {@code OilRepository.searchActiveOilsByName}、{@code SoapRepository.searchActiveSoapsByName} 對稱。
     */
    @Query("SELECT e FROM EssentialOil e WHERE e.isActive = true AND " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'")
    Page<EssentialOil> searchActiveEssentialOilsByName(@Param("keyword") String keyword, Pageable pageable);
}
