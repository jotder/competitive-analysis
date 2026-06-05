package com.antigravity.companalysis.api;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.shared.ParameterType;

/** Builds the comparison grid for a session by reading the catalog and delegating to the assembler. */
@Service
public class ComparisonGridService {

    /** Core comparison attributes shown by default. */
    static final List<ParameterType> DEFAULT_PARAMETERS = List.of(
            ParameterType.PRICE,
            ParameterType.CARPET_AREA,
            ParameterType.POSSESSION_DATE,
            ParameterType.BHK_CONFIG,
            ParameterType.AMENITIES);

    private final CatalogService catalog;
    private final ComparisonGridAssembler assembler;

    public ComparisonGridService(CatalogService catalog, ComparisonGridAssembler assembler) {
        this.catalog = catalog;
        this.assembler = assembler;
    }

    public GridResponse buildGrid(UUID sessionId) {
        SessionView session = catalog.getSession(sessionId);
        return assembler.assemble(
                sessionId,
                catalog.entities(session.entityIds()),
                catalog.baseObservations(session.entityIds()),
                DEFAULT_PARAMETERS);
    }
}
