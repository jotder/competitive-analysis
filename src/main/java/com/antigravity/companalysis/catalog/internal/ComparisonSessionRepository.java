package com.antigravity.companalysis.catalog.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonSessionRepository extends JpaRepository<ComparisonSession, UUID> {
}
