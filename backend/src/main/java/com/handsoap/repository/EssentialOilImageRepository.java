package com.handsoap.repository;

import com.handsoap.entity.EssentialOilImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EssentialOilImageRepository extends JpaRepository<EssentialOilImage, Long> {
    List<EssentialOilImage> findByEssentialOilId(Long essentialOilId);
}
