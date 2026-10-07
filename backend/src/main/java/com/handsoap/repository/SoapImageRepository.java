package com.handsoap.repository;

import com.handsoap.entity.SoapImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SoapImageRepository extends JpaRepository<SoapImage, Long> {
    List<SoapImage> findBySoapId(Long soapId);

    /** 全部被引用的圖檔檔名（孤兒檔掃描用；見 OrphanFileCleanupTask）。 */
    @Query("select i.imagePath from SoapImage i")
    List<String> findAllImagePaths();
}
