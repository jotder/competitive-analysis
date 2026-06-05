package com.antigravity.companalysis.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.antigravity.companalysis.api.GridResponse.CellStatus;
import com.antigravity.companalysis.catalog.CatalogService.EntityView;
import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * Pure logic test for the grid assembler — credibility-tier → cell-status mapping and gap detection.
 * Runs without Spring or a database.
 */
class ComparisonGridAssemblerTest {

    private final ComparisonGridAssembler assembler = new ComparisonGridAssembler();

    private static final UUID OURS = UUID.randomUUID();
    private static final UUID RIVAL = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    private static final List<ParameterType> PARAMS = List.of(
            ParameterType.PRICE, ParameterType.CARPET_AREA, ParameterType.AMENITIES);

    @Test
    void mapsBestTierToCellStatusAndDetectsGaps() {
        List<EntityView> entities = List.of(
                new EntityView(OURS, "Antigravity", "Skyline", "Thane West", "RERA-1", true),
                new EntityView(RIVAL, "Lodha", "Amara", "Thane West", "RERA-2", false));

        List<ObservationView> observations = List.of(
                // OURS: PRICE has both an authoritative (RERA) and an indicative (MagicBricks) value -> CREDIBLE
                obs(OURS, ParameterType.PRICE, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE),
                obs(OURS, ParameterType.PRICE, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE),
                // OURS: CARPET_AREA only user-provided -> USER_PROVIDED
                obs(OURS, ParameterType.CARPET_AREA, SourceSystem.USER, CredibilityTier.USER_PROVIDED),
                // OURS: AMENITIES missing -> gap
                // RIVAL: PRICE only indicative -> INDICATIVE
                obs(RIVAL, ParameterType.PRICE, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE));
                // RIVAL: CARPET_AREA + AMENITIES missing -> gaps

        GridResponse grid = assembler.assemble(SESSION, entities, observations, PARAMS);

        assertThat(grid.rows()).hasSize(2);

        var ours = grid.rows().get(0);
        assertThat(ours.ours()).isTrue();
        assertThat(ours.label()).isEqualTo("Antigravity — Skyline");
        assertThat(ours.cells().get(ParameterType.PRICE).status()).isEqualTo(CellStatus.CREDIBLE);
        assertThat(ours.cells().get(ParameterType.PRICE).bestTier()).isEqualTo(CredibilityTier.AUTHORITATIVE);
        assertThat(ours.cells().get(ParameterType.PRICE).observations()).hasSize(2);
        assertThat(ours.cells().get(ParameterType.CARPET_AREA).status()).isEqualTo(CellStatus.USER_PROVIDED);
        assertThat(ours.cells().get(ParameterType.AMENITIES).status()).isEqualTo(CellStatus.MISSING);

        var rival = grid.rows().get(1);
        assertThat(rival.cells().get(ParameterType.PRICE).status()).isEqualTo(CellStatus.INDICATIVE);

        // Gaps: OURS/AMENITIES, RIVAL/CARPET_AREA, RIVAL/AMENITIES
        assertThat(grid.gaps()).hasSize(3);
        assertThat(grid.gaps()).anySatisfy(g -> {
            assertThat(g.entityId()).isEqualTo(OURS);
            assertThat(g.parameter()).isEqualTo(ParameterType.AMENITIES);
        });
    }

    private static ObservationView obs(UUID entityId, ParameterType parameter,
                                       SourceSystem source, CredibilityTier tier) {
        return new ObservationView(UUID.randomUUID(), entityId, parameter, null, null, null,
                null, null, source, tier, null, null, null, Instant.EPOCH);
    }
}
