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

    /**
     * 多欄位關鍵字搜尋（review L3）。
     *
     * <p>{@code ESCAPE '\'} 讓 {@code :keyword} 內的 {@code %}、{@code _} 失去萬用字元語意、
     * 改為字面比對 —— keyword 一律經 {@link KeywordSanitizer#normalize(String)} 跳釋後才傳入。</p>
     *
     * <p><b>已知效能限制（刻意保留，review L3 明言「低頻站可先忍受」）</b>：前後的
     * {@code '%'} 使這個查詢無法使用 B-tree 索引，必為全表掃；{@code LOWER()} 包住欄位
     * 同樣使索引失效。要真正解決需改 MySQL {@code FULLTEXT}（ngram）或 Elasticsearch ——
     * 兩者語意都與「任意位置子字串」不同（不支援子字串、有最小 token 長度問題），
     * 屬破壞性變更，待搜尋量升上來再議。</p>
     */
    @Query("SELECT s FROM Soap s WHERE s.isActive = true AND (" +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' OR " +
            "LOWER(s.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' OR " +
            "LOWER(s.fullDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')")
    Page<Soap> searchActiveSoaps(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT s FROM Soap s WHERE s.isActive = true AND " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'")
    Page<Soap> searchActiveSoapsByName(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT s FROM Soap s WHERE s.isActive = true AND s.category.id = :categoryId AND (" +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' OR " +
            "LOWER(s.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' OR " +
            "LOWER(s.fullDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')")
    Page<Soap> searchActiveSoapsByCategory(@Param("categoryId") Long categoryId,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    List<Soap> findByCategoryId(Long categoryId);
}
