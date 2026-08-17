package com.westfield.api.billing.edge.adapter.out.sts;

import com.westfield.api.billing.edge.application.port.SecurityTokenService;
import com.westfield.api.billing.edge.application.port.ServiceAccountCredentials;
import com.westfield.api.billing.edge.config.BillingEdgeProperties;
import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.BackendAssertionProvider;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * The WS-Trust exchange with PingFederate (N-0010, N-0019, N-0073).
 *
 * <p>The legacy calls {@code module-pingfed:generate-saml}, an org-internal Exchange module that is
 * not in the source tree. Its wire protocol, timeouts, retry behaviour and caching are all unknown
 * (R-006). Everything observable is the call signature — username, password, {@code wsa_Address} —
 * and a response consumed as a {@code RequestSecurityTokenResponseCollection} carrying an
 * {@code Assertion} with {@code ID}, {@code IssueInstant} and {@code Version} (N-0073 ec 1). This
 * implementation is built to exactly that shape and to nothing more; the request envelope is an
 * ASSUMED contract and is stubbed to the same shape in the tests.
 *
 * <p>Two deliberate divergences, both recorded:
 * <ul>
 *   <li>the call is bounded by an explicit timeout. The legacy exposes none at all, so a hang is
 *       bounded only by an opaque module's defaults (N-0010 ec 1, N-0073 ec 2) — and since ADR-0007
 *       makes an STS failure fail the request, an unbounded wait would hold a caller open
 *       indefinitely (ADR-0014);</li>
 *   <li>a response that does not match the expected shape RAISES. The legacy resolves the assertion
 *       path to null and carries on: the WAS request then goes out with the header silently dropped
 *       and the ESB request with an empty assertion element — two different failure shapes for one
 *       root cause, both surfacing as the back end's 401 (N-0019 ec 1). ADR-0007 refuses to send an
 *       unauthenticated backend call.</li>
 * </ul>
 */
@Component
@MigratedFrom(value = "km:node/N-0073", note = "module-pingfed:generate-saml; shape assumed per N-0073 ec 1 (R-006)")
public class WsTrustSecurityTokenService implements SecurityTokenService {

    private final BillingEdgeProperties properties;
    private final HttpClient httpClient;

    public WsTrustSecurityTokenService(BillingEdgeProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(properties.getBackend().getSts().getConnectTimeout())
                .build());
    }

    WsTrustSecurityTokenService(BillingEdgeProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    @MigratedFrom(value = "km:node/N-0019", note = "audience scoping is what stops cross-environment replay")
    public Assertion mint(ServiceAccountCredentials credentials, String audience) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBackend().getSts().getHost()))
                    .timeout(properties.getBackend().getSts().getResponseTimeout())
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestSecurityToken(credentials, audience), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BackendAssertionProvider.BackendAssertionException(
                        "STS answered " + response.statusCode(), null);
            }
            return parse(response.body());
        } catch (BackendAssertionProvider.BackendAssertionException alreadyTyped) {
            throw alreadyTyped;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BackendAssertionProvider.BackendAssertionException("STS call interrupted", interrupted);
        } catch (Exception failure) {
            throw new BackendAssertionProvider.BackendAssertionException("STS call failed", failure);
        }
    }

    /**
     * The RST envelope. ASSUMED shape (R-006): the observable facts are the three inputs, so those
     * are what it carries and nothing is invented around them.
     */
    private static String requestSecurityToken(ServiceAccountCredentials credentials, String audience) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"\
                 xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"\
                 xmlns:wst="http://docs.oasis-open.org/ws-sx/ws-trust/200512"\
                 xmlns:wsa="http://www.w3.org/2005/08/addressing">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password>%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                    <wsa:To>%s</wsa:To>
                  </soapenv:Header>
                  <soapenv:Body>
                    <wst:RequestSecurityToken>
                      <wst:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</wst:RequestType>
                      <wst:TokenType>urn:oasis:names:tc:SAML:2.0:assertion</wst:TokenType>
                    </wst:RequestSecurityToken>
                  </soapenv:Body>
                </soapenv:Envelope>"""
                .formatted(escape(credentials.username()), escape(new String(credentials.password())), escape(audience));
    }

    @MigratedFrom(value = "km:node/N-0019", note = "RSTRC -> RequestedSecurityToken -> Assertion (ID/IssueInstant/Version)")
    private static Assertion parse(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // No external entities: the STS response is untrusted input like any other.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            NodeList assertions = document.getElementsByTagNameNS("*", "Assertion");
            if (assertions.getLength() == 0) {
                throw new BackendAssertionProvider.BackendAssertionException(
                        "STS response carries no Assertion element", null);
            }
            Element assertion = (Element) assertions.item(0);
            return new Assertion(
                    wsSecurityHeader(assertion),
                    assertion.getAttribute("ID"),
                    assertion.getAttribute("IssueInstant"),
                    assertion.getAttribute("Version"),
                    notOnOrAfter(assertion));
        } catch (BackendAssertionProvider.BackendAssertionException alreadyTyped) {
            throw alreadyTyped;
        } catch (Exception malformed) {
            throw new BackendAssertionProvider.BackendAssertionException(
                    "STS response did not match the expected WS-Trust shape", malformed);
        }
    }

    /** The assertion wrapped in the {@code wsse:Security} header the back ends require (TOK-002-c). */
    private static String wsSecurityHeader(Element assertion) throws Exception {
        StringWriter serialised = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(assertion), new StreamResult(serialised));
        return "<wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/"
                + "oasis-200401-wss-wssecurity-secext-1.0.xsd\" soapenv:mustUnderstand=\"1\" "
                + "xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + serialised + "</wsse:Security>";
    }

    /**
     * The validity horizon. The legacy never reads it (N-0019 ec 2) and neither does the target
     * unless the cache is enabled, because only a cache can outlive an assertion (ADR-0007).
     */
    private static Instant notOnOrAfter(Element assertion) {
        NodeList conditions = assertion.getElementsByTagNameNS("*", "Conditions");
        if (conditions.getLength() == 0) {
            return null;
        }
        String value = ((Element) conditions.item(0)).getAttribute("NotOnOrAfter");
        try {
            return value.isBlank() ? null : Instant.parse(value);
        } catch (DateTimeParseException unparseable) {
            return null;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
