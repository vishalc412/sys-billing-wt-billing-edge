package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.edge.domain.api.ContractViolation;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The API documentation console (N-0031, N-0032, N-0033).
 *
 * <p>The legacy serves this on a HARDCODED {@code /console/*}, <b>unconditionally in production</b>,
 * unaudited — publishing the contract of a customer-billing API to anyone who can reach the
 * listener. ADR-0038 corrects the production exposure and makes the path and enablement
 * configuration, and keeps everything else exactly as it was:
 * <ul>
 *   <li>an unknown console path answers 404 with the SAME fixed body the main API uses (N-0033);</li>
 *   <li>the console does NOT echo the correlation id, unlike every main-listener response
 *       (N-0032) — that difference is asserted, not tidied;</li>
 *   <li>console traffic remains UNAUDITED. Adding audit records for it would be an audit-stream
 *       change dressed up as a security fix, and it belongs to ADR-0017 (rejected alternative D);</li>
 *   <li>anything other than not-found falls through to the service's default failure handling, as it
 *       does today (N-0033 ec).</li>
 * </ul>
 *
 * <p>When the console is disabled the response is the same 404 as an unknown console path, so a
 * production probe cannot distinguish "disabled here" from "no such path" and thereby confirm the
 * console exists elsewhere.
 */
@RestController
@MigratedFrom(value = "km:node/N-0031", note = "sapi-billing-search-console; ADR-0038 config-gated")
public class ConsoleController {

    private final BillingEdgeProperties properties;
    private final ResourceLoader resourceLoader;

    public ConsoleController(BillingEdgeProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping(path = {"${billing.console.path:/console}", "${billing.console.path:/console}/**"})
    @MigratedFrom(value = "km:node/N-0032", note = "console listener: 200 by default, no correlation id echoed")
    public ResponseEntity<?> console() throws IOException {
        if (!properties.getConsole().isEnabled()) {
            // Disabled (production, by profile). Indistinguishable from an unknown path on purpose.
            return notFound();
        }
        Resource contract = resourceLoader.getResource(properties.getConsole().getContractLocation());
        if (!contract.exists()) {
            return notFound();
        }
        String yaml = new String(contract.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(browsableDocument(yaml));
    }

    private static ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ContractViolationWriter.body(ContractViolation.NOT_FOUND));
    }

    /** The published contract, browsable. No transformation: what is served is what is published. */
    private static String browsableDocument(String contract) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<title>sys-billing — API documentation console</title></head><body>"
                + "<h1>sys-billing — billing-edge</h1><pre>"
                + contract.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</pre></body></html>";
    }
}
