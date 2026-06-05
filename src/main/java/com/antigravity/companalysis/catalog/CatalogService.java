package com.antigravity.companalysis.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antigravity.companalysis.catalog.internal.ComparisonSession;
import com.antigravity.companalysis.catalog.internal.ComparisonSessionRepository;
import com.antigravity.companalysis.catalog.internal.DeveloperEntity;
import com.antigravity.companalysis.catalog.internal.DeveloperEntityRepository;
import com.antigravity.companalysis.catalog.internal.ParameterObservation;
import com.antigravity.companalysis.catalog.internal.ParameterObservationRepository;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * Public API of the {@code catalog} module: manage bounded comparison sessions, the 1–4 developer
 * entities they compare, and append-only parameter observations. Internal JPA entities are never
 * exposed — callers see the view/command records below.
 */
@Service
@Transactional
public class CatalogService {

    private final ComparisonSessionRepository sessionRepo;
    private final DeveloperEntityRepository entityRepo;
    private final ParameterObservationRepository observationRepo;

    public CatalogService(ComparisonSessionRepository sessionRepo,
                          DeveloperEntityRepository entityRepo,
                          ParameterObservationRepository observationRepo) {
        this.sessionRepo = sessionRepo;
        this.entityRepo = entityRepo;
        this.observationRepo = observationRepo;
    }

    public UUID createSession(String title, String microMarket) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("session title is required");
        }
        return sessionRepo.save(new ComparisonSession(title, microMarket)).getId();
    }

    @Transactional(readOnly = true)
    public SessionView getSession(UUID sessionId) {
        return toView(requireSession(sessionId));
    }

    /**
     * Create a developer entity and attach it to the session.
     *
     * @throws IllegalStateException if the session already has the maximum of 4 selected entities.
     */
    public UUID addEntity(UUID sessionId, String developerName, String projectName,
                          String microMarketArea, String reraRegistrationNumber, boolean ours) {
        ComparisonSession session = requireSession(sessionId);
        if (session.getSelectedEntityIds().size() >= 4) {
            throw new IllegalStateException("a comparison session may include at most 4 developers");
        }
        DeveloperEntity saved = entityRepo.save(
                new DeveloperEntity(developerName, projectName, microMarketArea, reraRegistrationNumber, ours));
        session.addEntityId(saved.getId());
        sessionRepo.save(session);
        return saved.getId();
    }

    /** Record one observation. If {@code tier} is null it defaults from the source system. */
    public UUID recordObservation(RecordObservationCommand cmd) {
        if (!entityRepo.existsById(cmd.entityId())) {
            throw new NoSuchElementException("unknown developer entity: " + cmd.entityId());
        }
        CredibilityTier tier = cmd.tier() != null ? cmd.tier() : cmd.source().defaultTier();
        ParameterObservation obs = new ParameterObservation(
                cmd.entityId(), cmd.parameter(), cmd.rawValue(), cmd.rawUnit(), cmd.areaBasis(),
                cmd.normalizedValue(), cmd.normalizedUnit(), cmd.source(), tier, cmd.confidence(),
                cmd.sourceRef(), cmd.scenarioId());
        return observationRepo.save(obs).getId();
    }

    @Transactional(readOnly = true)
    public List<EntityView> entities(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, DeveloperEntity> byId = entityRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(DeveloperEntity::getId, Function.identity()));
        // Preserve the session's selection order.
        return ids.stream().map(byId::get).filter(e -> e != null).map(CatalogService::toView).toList();
    }

    /** Factual observations (excludes what-if assumptions) for the given entities. */
    @Transactional(readOnly = true)
    public List<ObservationView> baseObservations(Collection<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        return observationRepo.findByEntityIdInAndScenarioIdIsNull(entityIds).stream()
                .sorted(Comparator.comparing(ParameterObservation::getObservedAt))
                .map(CatalogService::toView)
                .toList();
    }

    private ComparisonSession requireSession(UUID sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("unknown comparison session: " + sessionId));
    }

    private static SessionView toView(ComparisonSession s) {
        return new SessionView(s.getId(), s.getTitle(), s.getMicroMarket(),
                List.copyOf(s.getSelectedEntityIds()), s.getCreatedAt());
    }

    private static EntityView toView(DeveloperEntity e) {
        return new EntityView(e.getId(), e.getDeveloperName(), e.getProjectName(), e.getMicroMarketArea(),
                e.getReraRegistrationNumber(), e.isOurs());
    }

    private static ObservationView toView(ParameterObservation o) {
        return new ObservationView(o.getId(), o.getEntityId(), o.getParameter(), o.getRawValue(), o.getRawUnit(),
                o.getAreaBasis(), o.getNormalizedValue(), o.getNormalizedUnit(), o.getSource(), o.getTier(),
                o.getConfidence(), o.getSourceRef(), o.getScenarioId(), o.getObservedAt());
    }

    // ---- Public view / command records (the module's exported contract) ----

    public record SessionView(UUID id, String title, String microMarket,
                              List<UUID> entityIds, Instant createdAt) {
    }

    public record EntityView(UUID id, String developerName, String projectName, String microMarketArea,
                             String reraRegistrationNumber, boolean ours) {
    }

    public record ObservationView(UUID id, UUID entityId, ParameterType parameter, String rawValue,
                                  String rawUnit, AreaBasis areaBasis, BigDecimal normalizedValue,
                                  String normalizedUnit, SourceSystem source, CredibilityTier tier,
                                  BigDecimal confidence, String sourceRef, UUID scenarioId, Instant observedAt) {
    }

    public record RecordObservationCommand(UUID entityId, ParameterType parameter, String rawValue,
                                           String rawUnit, AreaBasis areaBasis, BigDecimal normalizedValue,
                                           String normalizedUnit, SourceSystem source, CredibilityTier tier,
                                           BigDecimal confidence, String sourceRef, UUID scenarioId) {
    }
}
