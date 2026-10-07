package com.handsoap.repository;

import com.handsoap.entity.EssentialOilImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EssentialOilImageRepository extends JpaRepository<EssentialOilImage, Long> {
    List<EssentialOilImage> findByEssentialOilId(Long essentialOilId);

    /** 全部被引用的圖檔檔名（孤兒檔掃描用；見 OrphanFileCleanupTask）。 */
    @Query("select i.imagePath from EssentialOilImage i")
    List<String> findAllImagePaths();
}
