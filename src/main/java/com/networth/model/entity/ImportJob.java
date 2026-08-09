package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "file_name")
    private String fileName;

    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "processed_records")
    @Builder.Default
    private Integer processedRecords = 0;

    @Builder.Default
    private Integer failedRecords = 0;

    @Column(name = "error_log")
    private String errorLog;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
