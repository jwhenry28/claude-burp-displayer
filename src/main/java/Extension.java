import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import core.LLMProviderRegistry;
import providers.ClaudeLLMProvider;
import ui.LLMRequestEditorProvider;
import ui.LLMResponseEditorProvider;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoyaApi.extension().setName("LLM Burp Extension");

        // Initialize the provider registry
        LLMProviderRegistry registry = LLMProviderRegistry.getInstance();

        // Register available LLM providers
        registry.registerProvider(new ClaudeLLMProvider());
        // Future providers can be registered here:
        // registry.registerProvider(new OpenAIProvider());
        // registry.registerProvider(new GeminiProvider());

        // Register the generic editors that will work with any provider
        LLMRequestEditorProvider requestProvider = new LLMRequestEditorProvider();
        LLMResponseEditorProvider responseProvider = new LLMResponseEditorProvider();

        montoyaApi.userInterface().registerHttpRequestEditorProvider(requestProvider);
        montoyaApi.userInterface().registerHttpResponseEditorProvider(responseProvider);
    }
}