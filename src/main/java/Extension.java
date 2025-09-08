import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoyaApi.extension().setName("Claude Burp Extension");

        ClaudeRequestEditorProvider requestProvider = new ClaudeRequestEditorProvider();
        ClaudeResponseEditorProvider responseProvider = new ClaudeResponseEditorProvider();
        
        montoyaApi.userInterface().registerHttpRequestEditorProvider(requestProvider);
        montoyaApi.userInterface().registerHttpResponseEditorProvider(responseProvider);
    }
}