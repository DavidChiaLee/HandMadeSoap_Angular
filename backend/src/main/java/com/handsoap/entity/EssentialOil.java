package com.handsoap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "essential_oils")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EssentialOil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 200)
    private String scentNotes;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    @Column(columnDefinition = "TEXT")
    private String precautions;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "essentialOil", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EssentialOilImage> images = new ArrayList<>();
}
