# NPCs

Learn how to create and manage AI-powered NPCs in your Minecraft world.

## Creating NPCs

### Basic NPC Creation

1. Stand where you want the NPC to appear
2. Run: `/chatr create <name>`
3. The NPC will be created at your location

Example:
```
/chatr create "Shopkeeper"
```

### NPC Configuration

Each NPC has its own configuration file at `plugins/Chatr/npcs/<name>/config.yml`, based on the bundled template:

```yaml
ai:
  # Optional NPC-specific endpoint (overrides global ai.endpoint)
  # Can be a provider name (e.g., "groq", "nvidia", "gemini") or direct URL
  # endpoint: "groq"

  # Optional NPC-specific model (overrides global ai.model)
  # model: "openai/gpt-oss-20b"

  # Optional NPC-specific generation settings
  # temperature: 0.8
  # top-p: 0.95
  # top-k: 40

# System Prompt (REQUIRED for AI responses)
system-prompt: |
  You are {npc_name}, a helpful NPC in a Minecraft world.
  Be friendly, stay in character, and answer questions about Minecraft.

# Enable per-player chat logging for this NPC
enable-chat-logging: true
```

## NPC Management

### Listing NPCs
Currently, there's no command to list all NPCs. Check the `plugins/Chatr/npcs/` directory or the `npcs.yml` file in the Chatr data folder for existing NPCs.

### Removing NPCs
```
/chatr remove <name>
```

### Reloading NPC Config
After editing an NPC's config file:
```
/chatr reload-npc <name>
```

### Changing NPC Skins
Set an NPC's skin to match a player's skin:
```
/chatr skin <npc-name> <player-name>
```
**Note**: NPC names in commands are case-sensitive. Use the exact name as specified when creating the NPC.
## NPC Behavior

### Interaction
- **Click to Chat**: Right-click the NPC armor stand to start a conversation
- **Chat Commands**: Use `/chatr <npc> ai <message>` to talk
- **Quick Reply**: Use `/chatr r <message>` to reply to your last NPC conversation

### Conversation Memory
- Each player has separate conversation history with each NPC
- Conversations persist across server restarts
- Use `/chatr clear <npc>` to reset conversation history

## Advanced NPC Features

### Custom Providers
Override the global AI endpoint for specific NPCs:

```yaml
ai:
  endpoint: "gemini"                       # Uses the Gemini provider
  model: "models/gemini-2.5-flash-lite"   # Example free-tier model
  temperature: 0.7
  max-tokens: 1000
```

### Custom System Prompts
Create detailed personalities using [[Context-Variables|context variables]]:

```yaml
system-prompt: |
  You are {name}, a wise librarian in the village library.
  Current time: {server_time}
  The player is in: {player_biome}
  Be helpful and knowledgeable about books and knowledge.
```

## NPC Files Structure

Chatr stores NPC configuration and logs under the plugin data folder:

```
plugins/Chatr/
├── npcs.yml                    # NPC locations, skins, and colors
├── npcs/
│   ├── Shopkeeper/
│   │   ├── config.yml          # NPC-specific AI settings
│   │   └── logs/               # Optional per-player chat logs
│   └── Librarian/
│       └── config.yml
└── conversations/
  └── <player-uuid>/
    └── <npc-name>.json     # Per-player, per-NPC conversation memory
```

## Best Practices

### NPC Design
- Give each NPC a clear, distinct personality
- Use context variables for dynamic responses
- Keep system prompts focused and specific
- Test conversations to ensure desired behavior

### Performance
- Use local AI models (LM Studio) to reduce costs
- Enable caching for frequently asked questions
- Monitor API usage with `/chatr stats`

### Management
- Regularly backup NPC configurations
- Use descriptive names for easy management
- Document custom NPC behaviors for other admins

## Troubleshooting

### NPC Not Responding
- Check that the NPC is enabled in its config
- Verify AI provider is working (test with `/chatr stats`)
- Check for configuration errors in console

### Conversations Not Saving
- Ensure the conversations directory has write permissions
- Check that `conversation.persistence-enabled` is true

### Skin Not Updating
- The target player must have joined the server
- Player skins update automatically when they join

## See Also

- [[Commands|Command Reference]]
- [[Context-Variables|Context Variables]]
- [[API-Providers|AI Provider Setup]]