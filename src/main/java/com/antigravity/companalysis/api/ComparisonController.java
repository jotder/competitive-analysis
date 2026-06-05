package com.antigravity.companalysis.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.RecordObservationCommand;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * Comparison sessions: create, add 1–4 developers, hand-enter observations, and read the grid.
 * (Targeted collection and what-if scenarios arrive in later delivery steps.)
 */
@RestController
@RequestMapping("/api/comparisons")
public class ComparisonController {

    private final CatalogService catalog;
    private final ComparisonGridService grid;

    public ComparisonController(CatalogService catalog, ComparisonGridService grid) {
        this.catalog = catalog;
        this.grid = grid;
    }

    @PostMapping
    public IdResponse create(@RequestBody CreateSession req) {
        return new IdResponse(catalog.createSession(req.title(), req.microMarket()));
    }

    @GetMapping("/{id}")
    public SessionView get(@PathVariable UUID id) {
        return catalog.getSession(id);
    }

    @PostMapping("/{id}/entities")
    public IdResponse addEntity(@PathVariable UUID id, @RequestBody CreateEntity req) {
        UUID entityId = catalog.addEntity(id, req.developerName(), req.projectName(),
                req.microMarketArea(), req.reraRegistrationNumber(), req.ours());
        return new IdResponse(entityId);
    }

    @PostMapping("/{id}/observations")
    public IdResponse addObservation(@PathVariable UUID id, @RequestBody CreateObservation req) {
        UUID observationId = catalog.recordObservation(new RecordObservationCommand(
                req.entityId(), req.parameter(), req.rawValue(), req.rawUnit(), req.areaBasis(),
                req.normalizedValue(), req.normalizedUnit(), req.source(), req.tier(),
                req.confidence(), req.sourceRef(), null));
        return new IdResponse(observationId);
    }

    @GetMapping("/{id}/grid")
    public GridResponse grid(@PathVariable UUID id) {
        return grid.buildGrid(id);
    }

    // ---- request/response payloads ----

    public record CreateSession(String title, String microMarket) {
    }

    public record CreateEntity(String developerName, String projectName, String microMarketArea,
                               String reraRegistrationNumber, boolean ours) {
    }

    public record CreateObservation(UUID entityId, ParameterType parameter, String rawValue, String rawUnit,
                                    AreaBasis areaBasis, BigDecimal normalizedValue, String normalizedUnit,
                                    SourceSystem source, CredibilityTier tier, BigDecimal confidence,
                                    String sourceRef) {
    }

    public record IdResponse(UUID id) {
    }
}
