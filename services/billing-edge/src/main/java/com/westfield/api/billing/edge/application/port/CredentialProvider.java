package com.westfield.api.billing.edge.application.port;

import com.westfield.api.billing.platform.observability.MigratedFrom;

/**
 * Reads the billing service-account credentials from the corporate secret store (N-0020, N-0072).
 *
 * <p>ADR-0008 keeps Thycotic as the source of truth at cutover, read with a short TTL cache so that
 * a rotation still takes effect without a redeploy while the vault call rate stays bounded. The
 * legacy's caching and outage behaviour is internal to an Exchange module and cannot be determined
 * from source (N-0008 ec 1, N-0020 ec, R-007), so the target states its own behaviour explicitly
 * rather than inheriting an unknown.
 */
@MigratedFrom(value = "km:node/N-0020", note = "Thycotic Secret Server; ADR-0008 TTL cache")
public interface CredentialProvider {

    /**
     * @return the credentials, to be closed by the caller as soon as the exchange has finished
     * @throws CredentialResolutionException when a secret name cannot be resolved or the store
     *         cannot be reached. Never returns a partially populated value: the legacy has no
     *         failure branch at all (N-0072 ec 2) and a silent empty credential would surface as a
     *         backend authorisation error rather than as what it is (TOK-001-d).
     */
    ServiceAccountCredentials serviceAccount();

    /** Raised when credential resolution fails. Attributable in the log, never mistaken for a backend fault. */
    class CredentialResolutionException extends RuntimeException {
        public CredentialResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
