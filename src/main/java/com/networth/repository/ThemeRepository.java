package com.networth.repository;

import com.networth.model.entity.Theme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ThemeRepository extends JpaRepository<Theme, UUID> {
    List<Theme> findByIsSystemTrueOrUserIdOrderByName(UUID userId);
}
