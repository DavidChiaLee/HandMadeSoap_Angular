package com.handsoap.repository;

import com.handsoap.entity.OilImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OilImageRepository extends JpaRepository<OilImage, Long> {
    List<OilImage> findByOilId(Long oilId);
}
