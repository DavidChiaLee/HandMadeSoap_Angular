package com.handsoap.repository;

import com.handsoap.entity.SoapImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoapImageRepository extends JpaRepository<SoapImage, Long> {
    List<SoapImage> findBySoapId(Long soapId);
}
