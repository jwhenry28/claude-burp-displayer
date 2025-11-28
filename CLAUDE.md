# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Burp Suite extension that provides formatted display of LLM conversation messages within Burp's HTTP history and Repeater tabs. The extension uses a provider-based architecture to support multiple LLM APIs, currently supporting Claude (Anthropic) with the ability to easily add support for other providers like OpenAI, Google Gemini, etc.

## Build Commands

```bash
# Build the extension JAR (default task)
./gradlew

# Build explicitly
./gradlew build

# Clean build artifacts
./gradlew clean
```

The compiled JAR is output to `build/libs/claude-burp-displayer.jar`.

**IMPORTANT**: Always build after making significant edits and address all compilation problems before considering work complete.

## Architecture

### Provider-Based Architecture

The extension uses a flexible provider-based system to support multiple LLM APIs:

- **core/** - Core interfaces and data models
  - `LLMProvider.java`: Interface that all providers must implement
  - `ConversationMessage.java`: Generic message representation
  - `ContentItem.java` and subclasses: Different content types (text, tool calls, etc.)
  - `LLMProviderRegistry.java`: Manages and auto-detects providers

- **providers/** - LLM provider implementations
  - `ClaudeLLMProvider.java`: Claude/Anthropic API implementation

- **ui/** - Generic UI components
  - `LLMRequestEditor.java` / `LLMResponseEditor.java`: Generic editors that work with any provider
  - `LLMConversationRenderer.java`: Provider-agnostic conversation rendering
  - `UIUtils.java`: Search functionality and UI utilities

### Core Components

- **Extension.java**: Entry point that registers providers and UI components

- **LLMProvider Interface**: Each provider implements:
  - `isProviderMessage()`: Detects if traffic belongs to this provider
  - `parseRequest()` / `parseResponse()`: Parses provider-specific formats
  - `getProviderConfig()`: Returns UI configuration (colors, icons, etc.)

### Detection Criteria

The extension identifies Claude messages based on:
- POST request to `api.anthropic.com`
- Path starts with `/v1/messages`
- Request Content-Type: `application/json`
- Response Content-Type: `text/event-stream` (streaming) or `application/json` (non-streaming)

### Message Format

Supports four roles:
- **system**: Collapsible orange panels (system prompts)
- **tools**: Collapsible green panels showing available tools (parsed from request `tools` array)
- **user**: Blue labels with content
- **assistant**: Purple labels with content

Content types include:
- **text**: Standard text content
- **tool_definition**: Collapsible green panels showing tool name, description, and input schema
- **tool_use**: Collapsible blue panels showing tool calls with ID and input JSON
- **tool_result**: Collapsible purple panels showing tool execution output

### Response Parsing

The response parser supports two formats:

**Streaming (SSE)**: Handles Server-Sent Events by:
1. Tracking `content_block_start` events to create content blocks
2. Accumulating `content_block_delta` events (partial_json, text, thinking)
3. Finalizing blocks on `content_block_stop` events
4. Supporting multiple content types: text, tool_use, thinking

**Non-streaming (JSON)**: Parses the complete JSON response:
1. Extracts the `content` array from the response
2. Parses each content item (text, tool_use, tool_result)
3. Displays the complete assistant message

### Search Implementation

The `UIUtils.SearchHighlighter` class provides:
- Full-text search with regex support
- Case-sensitive/insensitive modes
- Match highlighting (yellow for all, orange for current)
- Automatic expansion of collapsed panels when matches found inside
- Keyboard navigation (Enter/Shift+Enter, Ctrl+F to open)

## Key Technologies

- **Burp Montoya API 2025.7**: Modern Burp extension API
- **Jackson 2.17.2**: JSON parsing and manipulation
- **Java Swing**: UI components
- **Java 17**: Source and target compatibility

## API Reference

If uncertain how to implement Burp Suite functionality, refer to: https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html

## Adding New LLM Providers

To add support for a new LLM API:

1. Create a new class in `providers/` implementing `LLMProvider`
2. Implement detection logic in `isProviderMessage()`
3. Implement request/response parsing for the provider's format
4. Configure colors and icons in `getProviderConfig()`
5. Register the provider in `Extension.java`

Example:
```java
// In Extension.java
registry.registerProvider(new OpenAIProvider());
registry.registerProvider(new GeminiProvider());
```

## Development Workflow

1. Make code changes
2. Run `./gradlew build`
3. In Burp Suite: Extensions > Installed > Hold Ctrl/⌘ and toggle the extension's "Loaded" checkbox to reload

## Proxying Setup

To test with Claude Code:
```bash
export HTTP_PROXY=http://127.0.0.1:8080
export HTTPS_PROXY=http://127.0.0.1:8080
export NODE_TLS_REJECT_UNAUTHORIZED=0
```

Optional Burp settings:
- Network > HTTP > Streaming responses: add `api.anthropic.com`
- Network > HTTP > HTTP/2: uncheck "Default to HTTP/2 if the server supports it"
