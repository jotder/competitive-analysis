package com.antigravity.companalysis.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterObservationRepository extends JpaRepository<ParameterObservation, UUID> {

    /** Factual observations (excludes what-if assumptions) for the given entities — feeds the base grid. */
    List<ParameterObservation> findByEntityIdInAndScenarioIdIsNull(Collection<UUID> entityIds);

    List<ParameterObservation> findByEntityId(UUID entityId);
}
