# ServerAI

The ServerAI is a server-wide AI assistant that monitors chat and helps players proactively.

## Overview

ServerAI provides:
- Chat monitoring and automatic responses
- Proactive help and conversation joining
- Server-wide assistance without specific NPC interactions
- Configurable personality and behavior

## Enabling ServerAI

Set in `config.yml`:

```yaml
server-ai:
  enabled: true
  name: "Heimdall"
```

## Configuration Options

### Basic Settings

```yaml
server-ai:
  enabled: true
  name: "Heimdall"                    # Assistant name
  display:
    show-prefix: true                 # Show [AI] prefix
    prefix-text: "AI"                 # Prefix text
    prefix-color: "&6"                # Gold color
    name-color: "&a"                  # Green color
```

### Chat Monitoring

```yaml
server-ai:
  chat-scan-interval-seconds: 60      # Check chat every 60 seconds
  conversation-join-chance: 0.1       # 10% chance to join conversations
  max-chat-history: 25                # Remember last 25 messages
  max-conversation-memory: 10         # Messages per player conversation
```

### AI Settings

```yaml
server-ai:
  server-url: "http://localhost:1234"  # Independent of global ai.endpoint
  model: "local-model"                 # Independent of global ai.model
  max-tokens: 2000                      # Response length limit
```

## How ServerAI Works

### Chat Monitoring
- Scans recent chat messages periodically
- Looks for opportunities to help or join conversations
- Responds to direct mentions of its name

### Conversation Joining
- Randomly joins ongoing conversations based on `conversation-join-chance`
- Provides helpful information or mediates conflicts
- Only responds when it can add value

### Direct Interaction
- Players can mention the AI by name in chat
- Example: "Heimdall, how do I make a diamond pickaxe?"
- AI responds naturally in chat

## System Prompt

Customize the AI's personality and behavior:

```yaml
server-ai:
  system-prompt-source: "inline"  # "inline" or "file"
  system-prompt: |
    You are {name}, the Server AI assistant for this Minecraft server.

    RESPONSE RULES:
    - Respond ONLY with your message. No analysis, no thinking, no prefixes.
    - Do NOT include location, coordinates, biome, weather, or time information unless asked.
    - Keep responses focused on the player's query.
    - Just respond naturally as if you're chatting.
    - Use plain text only. No markdown formatting.

    Your personality:
    - Wise and calm, like an all-seeing guardian
    - Helpful and knowledgeable about Minecraft
    - Friendly but professional
```

## Context Variables

ServerAI supports the same [context variables](Context-Variables.md) as NPCs:

- `{name}` - AI's name
- `{server_time}` - Current server time
- `{player_name}` - Mentioning player's name
- `{weather}` - Current weather
- And more...

## Limitations

### AI Provider Compatibility
**Important**: ServerAI only works with OpenAI-compatible HTTP APIs:
- ✅ LM Studio (default)
- ✅ Ollama
- ✅ Groq
- ✅ Mistral
- ✅ NVIDIA
- ✅ OpenRouter
- ❌ Gemini (not compatible)

For Gemini support, use individual NPCs instead.

### File-based Prompts
You can load prompts from a file:

```yaml
server-ai:
  system-prompt-source: "file"
```

Then create `plugins/Chatr/serverAI/system-prompt.txt` with your custom prompt.

## Best Practices

### Personality Design
- Keep the personality consistent and helpful
- Use context variables for dynamic responses
- Focus on being a positive community presence

### Performance Tuning
- Adjust `chat-scan-interval-seconds` based on server activity
- Lower `conversation-join-chance` for quieter servers
- Monitor API usage with `/chatr stats`

### Community Management
- Use ServerAI to welcome new players
- Have it provide general server information
- Configure it to help with common questions

## Troubleshooting

### ServerAI Not Responding
- Check that `server-ai.enabled` is `true`
- Verify AI provider is configured and working
- Ensure using OpenAI-compatible API (not Gemini)

### Not Joining Conversations
- Check `conversation-join-chance` is above 0
- Verify `chat-scan-interval-seconds` is reasonable
- Look for console errors during chat scanning

### Wrong Personality
- Check system prompt for correct formatting
- Ensure context variables are properly used
- Test with direct mentions first

## See Also

- [ServerAI Configuration](Configuration.md)
- [Available Variables](Context-Variables.md)
- [AI Provider Setup](API-Providers.md)
- [Common Issues](Troubleshooting.md)