package io.radius.search.api;

import io.radius.common.security.AuthUser;
import org.springframework.lang.Nullable;
import io.radius.common.web.ApiException;
import io.radius.search.service.SavedSearchService;
import io.radius.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Search", description = "Ask in a sentence or filter by hand — one endpoint either way")
public class SearchController {

    private final SearchService search;
    private final SavedSearchService saved;

    public SearchController(SearchService search, SavedSearchService saved) {
        this.search = search;
        this.saved = saved;
    }

    @PostMapping("/api/search")
    @Operation(summary = "Search listings and members; the response echoes what the parser understood")
    public Dtos.SearchResponse search(@Nullable AuthUser me, @Valid @RequestBody Dtos.SearchRequest req) {
        return search.search(me == null ? null : me.id(), req);
    }

    @GetMapping("/api/search/map")
    @Operation(summary = "Pins inside a bounding box, clustered server-side above 200 results")
    public Dtos.MapResponse map(@RequestParam String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw ApiException.badRequest("bad_bbox", "bbox must be south,west,north,east");
        }
        try {
            return search.map(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("bad_bbox", "bbox must be four numbers");
        }
    }

    @PostMapping("/api/saved-searches")
    @Operation(summary = "Save a search and get a digest when something new matches")
    public Dtos.SavedSearchDto save(AuthUser me, @Valid @RequestBody Dtos.SaveSearchRequest req) {
        return saved.save(me.id(), req);
    }

    @GetMapping("/api/saved-searches")
    public List<Dtos.SavedSearchDto> mine(AuthUser me) {
        return saved.mine(me.id());
    }

    @DeleteMapping("/api/saved-searches/{id}")
    public ResponseEntity<Void> delete(AuthUser me, @PathVariable UUID id) {
        saved.delete(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}
