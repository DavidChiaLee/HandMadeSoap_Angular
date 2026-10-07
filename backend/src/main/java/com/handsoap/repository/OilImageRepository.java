package com.handsoap.repository;

import com.handsoap.entity.OilImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OilImageRepository extends JpaRepository<OilImage, Long> {
    List<OilImage> findByOilId(Long oilId);

    /** 全部被引用的圖檔檔名（孤兒檔掃描用；見 OrphanFileCleanupTask）。 */
    @Query("select i.imagePath from OilImage i")
    List<String> findAllImagePaths();
}
