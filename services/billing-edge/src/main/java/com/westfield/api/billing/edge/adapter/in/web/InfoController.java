package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.adapter.out.build.BuildMetadataAdapter;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /info} — which build is running (N-0043, N-0044).
 *
 * <p>The only endpoint that calls no back end and obtains no security assertion. It nonetheless
 * REQUIRES a bearer token, because the legacy covers it with the {@code secured} trait, so it cannot
 * serve as an unauthenticated health probe (N-0043 ec 1). That is preserved rather than relaxed:
 * Kubernetes-style probes use the actuator endpoints. Relaxing it would publish build provenance —
 * including the source commit — to anyone who can reach the port.
 *
 * <p>It sets no status of its own and relies on the listener default of 200 (N-0043 ec 2).
 */
@RestController
@MigratedFrom(value = "km:node/N-0043", note = "get:/info flow; bearer token still required")
public class InfoController {

    private final BuildMetadataAdapter buildMetadata;

    public InfoController(BuildMetadataAdapter buildMetadata) {
        this.buildMetadata = buildMetadata;
    }

    @GetMapping(path = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    @MigratedFrom(value = "km:node/N-0044", note = "setBuildInfo projection")
    public ResponseEntity<Map<String, Object>> buildInformation() {
        return ResponseEntity.ok(buildMetadata.current().asMap());
    }
}
