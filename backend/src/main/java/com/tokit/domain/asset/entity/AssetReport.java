package com.tokit.domain.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "asset_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AssetReport(Asset asset, String title, String filePath, LocalDateTime createdAt) {
        this.asset = asset;
        this.title = title;
        this.filePath = filePath;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }
}
