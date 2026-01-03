# Context Variables

Context variables allow you to create dynamic, personalized responses by including real-time information in your AI prompts.

## Available Variables

Chatr exposes slightly different variables for ServerAI and NPC system prompts.

### ServerAI Variables
- `{name}` – The ServerAI's configured name
- `{server_time}` – Current server time in 24‑hour format (HH:MM)
- `{server_time_ticks}` – Raw server time in ticks
- `{server_weather}` – Current server weather (`clear`, `rain`, `thunderstorm`)
- `{player_name}` – Name of the player being responded to
- `{player_time}` – Player time approximation in 24‑hour format (HH:MM)
- `{player_time_ticks}` – Time in ticks used for the player
- `{player_weather}` – Weather from the player's world (same values as `{server_weather}`)
- `{player_biome}` – Biome the player is currently in (e.g. `plains`, `dark forest`)
- `{player_x}` – Player X coordinate (formatted to one decimal place)
- `{player_y}` – Player Y coordinate (formatted to one decimal place)
- `{player_z}` – Player Z coordinate (formatted to one decimal place)

### NPC System-Prompt Variables
From the NPC template (`npc-config-template.yml`):

**NPC context:**
- `{npc_name}` – The NPC's name
- `{time}` – Time of day (e.g. `morning`, `day`, `dusk`, `night`, `dawn`)
- `{time_exact}` – Time in 24‑hour format (HH:MM)
- `{weather}` – Current weather (`clear`, `rain`, `thunderstorm`)
- `{biome}` – Biome where the NPC is located
- `{world}` – World name

**Player context:**
- `{player_name}` – Player's username
- `{player_health}` – Player health value
- `{player_max_health}` – Player maximum health
- `{player_hunger}` – Player hunger value
- `{player_level}` – Player XP level
- `{player_gamemode}` – Player game mode (survival/creative/etc.)
- `{player_biome}` – Biome where the player is located

> **Note**: The `{time_ticks}` variable shown in the NPC template is documented but not currently implemented. Use `{time_exact}` for time information.

## Usage Examples

### Basic Personalization
```
You are {name}, a friendly shopkeeper.
Hello {player_name}! How can I help you today?
```

### Time-Aware Responses
```
The current server time is {server_time}.
Your personal time is set to {player_time}.
```

### Location Context
```
I see you're in the {player_biome} biome at coordinates {player_x}, {player_y}, {player_z}.
The weather is currently {weather}.
```

### Combined Example
```
You are {name}, the guardian of this Minecraft server.

Current status:
- Server time: {server_time}
- Player: {player_name}
- Location: {player_biome} biome
- Weather: {weather}

How can I assist you today?
```

## Where to Use Variables

### System Prompts
Variables work in:
- ServerAI system prompt in `config.yml`
- Individual NPC system prompts in `npcs/<name>/config.yml`
- File-based prompts (`.txt` files)

### Configuration Examples

#### ServerAI System Prompt
```yaml
server-ai:
  system-prompt: |
    You are {name}, the server assistant.
    Current time: {server_time}
    Weather: {server_weather}
    Be helpful and mention the current conditions when relevant.
```

#### NPC System Prompt
```yaml
# In plugins/Chatr/npcs/Shopkeeper/config.yml
system-prompt: |
  You are a merchant in the {player_biome} biome.
  The time is {time_exact}.
  The player is currently in: {player_biome}.
  Offer items appropriate to the current weather: {weather}.
```

## Best Practices

### Selective Usage
- Don't overuse variables - they can make responses verbose
- Only include location/time info when relevant to the conversation
- Consider player privacy when using location data

### Formatting
- Variables are case-sensitive: `{player_name}` not `{PLAYER_NAME}`
- No spaces around variable names: `{player_name}` not `{ player_name }`
- Variables work in multi-line prompts

### Testing
- Test prompts with different conditions
- Use `/chatr stats` to monitor API usage
- Enable debug mode to see variable substitution

## Variable Details

### Time Formats
- `{server_time}` / `{time_exact}`: 24‑hour format (HH:MM)
- `{player_time}`: Uses world time as an approximation of player time

### Coordinate Precision
- Coordinates are formatted to one decimal place

### Biome Names
- Uses Minecraft's biome registry names (lowercase, spaces instead of underscores)
- Examples: `plains`, `forest`, `dark forest`, `cherry grove`

### Weather Values
- `clear` - No precipitation
- `rain` - Raining
- `thunderstorm` - Thunderstorm (includes rain)

## Limitations

### ServerAI Restrictions
ServerAI's default prompt instructs the model to:
- Avoid including location/time/coordinates unless specifically asked
- Focus on relevant context only
- Keep responses concise

### Update Frequency
- Variables update in real-time
- No caching of variable values
- Each conversation uses current values

## See Also

- [[ServerAI|ServerAI Configuration]]
- [[NPCs|NPC System Prompts]]
- [[Configuration|Prompt Customization]]
- [[Troubleshooting|Variable Issues]]