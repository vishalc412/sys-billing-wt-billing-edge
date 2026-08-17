package com.westfiled.api.billing.configs;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction (Caffeine, configured via spring.cache.caffeine.spec in
 * application.yaml). Not currently applied to any endpoint: none of the Mule flows reviewed cached
 * responses, and these endpoints return live financial/billing data (account balance, past-due
 * amounts) where a stale cached read would be incorrect. Left wired up per the documented stack
 * for future use rather than applied speculatively.
 */
@Configuration
@EnableCaching
public class CachingConfig {
}
