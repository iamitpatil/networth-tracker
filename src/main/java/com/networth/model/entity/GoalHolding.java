package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "goal_holdings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "holding_id", nullable = false)
    private UUID holdingId;

    @Column(name = "allocation_pct", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal allocationPct = BigDecimal.valueOf(100);

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
