package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "symbols")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Symbol {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(length = 100)
    private String sector;

    @Column(length = 12)
    private String isin;

    @Column(name = "scheme_code", length = 10)
    private String schemeCode;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
