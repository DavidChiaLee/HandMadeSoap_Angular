package com.handsoap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "soap_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoapImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soap_id", nullable = false)
    private Soap soap;

    @Column(nullable = false, length = 500)
    private String imagePath;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
