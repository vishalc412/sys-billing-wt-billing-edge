package com.westfiled.api.billing.soap;

import org.apache.commons.text.StringSubstitutor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Loads XML templates from {@code src/main/resources/template/} and fills in {@code ${TOKEN}}
 * placeholders, replacing the DataWeave-built SOAP/SAML payloads in the Mule flows.
 */
@Service
public class TemplateService {

    private final ConcurrentMap<String, String> templateCache = new ConcurrentHashMap<>();

    public String render(String templateClasspathLocation, Map<String, String> values) {
        String template = templateCache.computeIfAbsent(templateClasspathLocation, this::load);
        return new StringSubstitutor(values).replace(template);
    }

    private String load(String location) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load template " + location, e);
        }
    }
}
