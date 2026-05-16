package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "symbol_aliases", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "source"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymbolAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, length = 50)
    private String alias;
}
