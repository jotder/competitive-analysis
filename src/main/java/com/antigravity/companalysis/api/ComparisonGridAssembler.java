package com.antigravity.companalysis.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.api.GridResponse.Cell;
import com.antigravity.companalysis.api.GridResponse.CellStatus;
import com.antigravity.companalysis.api.GridResponse.Gap;
import com.antigravity.companalysis.api.GridResponse.Row;
import com.antigravity.companalysis.catalog.CatalogService.EntityView;
import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;

/**
 * Pure, deterministic assembly of the comparison grid from entities + observations. No Spring or database
 * dependency — fully unit-testable (aligns with ADR-0001: logic is deterministic and verifiable).
 */
@Component
public class ComparisonGridAssembler {

    public GridResponse assemble(UUID sessionId,
                                 List<EntityView> entities,
                                 List<ObservationView> observations,
                                 List<ParameterType> parameters) {

        Map<UUID, List<ObservationView>> obsByEntity = observations.stream()
                .collect(Collectors.groupingBy(ObservationView::entityId));

        List<Row> rows = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>();

        for (EntityView entity : entities) {
            Map<ParameterType, List<ObservationView>> obsByParam = obsByEntity
                    .getOrDefault(entity.id(), List.of()).stream()
                    .collect(Collectors.groupingBy(ObservationView::parameter));

            Map<ParameterType, Cell> cells = new LinkedHashMap<>();
            for (ParameterType parameter : parameters) {
                Cell cell = toCell(obsByParam.getOrDefault(parameter, List.of()));
                cells.put(parameter, cell);
                if (cell.status() == CellStatus.MISSING) {
                    gaps.add(new Gap(entity.id(), parameter));
                }
            }
            rows.add(new Row(entity.id(), label(entity), entity.ours(), cells));
        }
        return new GridResponse(sessionId, parameters, rows, gaps);
    }

    private static Cell toCell(List<ObservationView> values) {
        if (values.isEmpty()) {
            return new Cell(CellStatus.MISSING, null, List.of());
        }
        CredibilityTier best = values.stream()
                .map(ObservationView::tier)
                .max(Comparator.comparingInt(CredibilityTier::rank))
                .orElseThrow();
        CellStatus status = switch (best) {
            case AUTHORITATIVE -> CellStatus.CREDIBLE;
            case USER_PROVIDED -> CellStatus.USER_PROVIDED;
            case INDICATIVE -> CellStatus.INDICATIVE;
            // Assumptions are scenario-scoped and excluded from the base grid; treat as no factual value.
            case ASSUMPTION -> CellStatus.MISSING;
        };
        return new Cell(status, best, List.copyOf(values));
    }

    private static String label(EntityView e) {
        return e.projectName() == null || e.projectName().isBlank()
                ? e.developerName()
                : e.developerName() + " — " + e.projectName();
    }
}
