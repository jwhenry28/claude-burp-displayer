package core;

import burp.api.montoya.http.message.HttpRequestResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registry for managing LLM providers and automatically detecting the appropriate provider
 * for a given HTTP request/response.
 */
public class LLMProviderRegistry {
    private static LLMProviderRegistry instance;
    private final List<LLMProvider> providers;

    private LLMProviderRegistry() {
        this.providers = new ArrayList<>();
    }

    public static synchronized LLMProviderRegistry getInstance() {
        if (instance == null) {
            instance = new LLMProviderRegistry();
        }
        return instance;
    }

    /**
     * Registers a new LLM provider.
     */
    public void registerProvider(LLMProvider provider) {
        if (provider != null && !providers.contains(provider)) {
            providers.add(provider);
        }
    }

    /**
     * Unregisters an LLM provider.
     */
    public void unregisterProvider(LLMProvider provider) {
        providers.remove(provider);
    }

    /**
     * Finds the appropriate provider for the given request/response.
     */
    public Optional<LLMProvider> findProvider(HttpRequestResponse requestResponse) {
        if (requestResponse == null) {
            return Optional.empty();
        }

        for (LLMProvider provider : providers) {
            if (provider.isProviderMessage(requestResponse)) {
                return Optional.of(provider);
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if any provider can handle the given request/response.
     */
    public boolean hasProvider(HttpRequestResponse requestResponse) {
        return findProvider(requestResponse).isPresent();
    }

    /**
     * Gets all registered providers.
     */
    public List<LLMProvider> getAllProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Clears all registered providers.
     */
    public void clearProviders() {
        providers.clear();
    }
}