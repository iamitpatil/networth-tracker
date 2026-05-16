package com.networth.model.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FamilyResponse {
    private UUID id;
    private String name;
    private UUID createdBy;
    private String createdByName;
    private int memberCount;
    private int approvedCount;
    private LocalDateTime createdAt;
}
