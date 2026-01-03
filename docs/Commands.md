# Commands

Reference of all available Chatr commands.

## Admin Commands

### Plugin Management
```
/chatr version
```
Shows the current plugin version.

```
/chatr reload
```
Reloads the main configuration file. Requires `chatr.reload` permission.

### NPC Management
```
/chatr create <name>
```
Creates a new NPC at your current location. Requires `chatr.create` permission.

```
/chatr remove <name>
```
Removes the specified NPC. Requires `chatr.remove` permission.

```
/chatr skin <npc> <player>
```
Sets the NPC's skin to match the specified player's skin. Requires `chatr.skin` permission.

```
/chatr reload-npc <name>
```
Reloads the configuration for a specific NPC. Requires `chatr.reload` permission.
**Note**: NPC names in commands are case-sensitive. Use the exact name as specified when creating the NPC.
### Debug Information
```
/chatr info
```
Shows debug information about NPCs and writes a detailed log file. Requires `chatr.admin` permission.

### Monitoring & Statistics
```
/chatr stats [summary|npcs|players|npc <name>]
```
Displays API usage statistics and performance metrics. Requires `chatr.admin` permission.

```
/chatr cache [stats|clear [all|<pattern>]]
```
- `stats`: Shows cache statistics
- `clear`: Clears the response cache (optionally with pattern)
Requires `chatr.admin` permission.

```
/chatr serverai [status|refresh|clear]
```
ServerAI management commands. Requires `chatr.admin` permission.
- `status`: Show ServerAI status
- `refresh`: Refresh ServerAI in tab list
- `clear`: Clear all ServerAI conversations

```
/chatr <npc> ai <message>
```
Send a message to a specific NPC (admin only). Requires `chatr.admin` permission.

Example:
```
/chatr Shopkeeper ai Do you have any diamonds for sale?
```

## Player Commands

### NPC Interaction
```
/chatr r <message>
```
Quick reply to the last NPC you chatted with. Requires `chatr.ai` permission.

Example:
```
/chatr r How much does it cost?
```

### Conversation Management
```
/chatr clear <npc>
```
Clears your conversation history with the specified NPC. Requires `chatr.ai` permission.

## Command Aliases

- `/chatr r` is a shortcut for replying to NPCs

## Permission Requirements

| Command | Permission | Default |
|---------|------------|---------|
| `/chatr version` | `chatr.use` | Everyone |
| `/chatr reload` | `chatr.reload` | OP only |
| `/chatr create` | `chatr.create` | OP only |
| `/chatr remove` | `chatr.remove` | OP only |
| `/chatr skin` | `chatr.skin` | OP only |
| `/chatr color` | `chatr.color` | OP only |
| `/chatr reload-npc` | `chatr.reload` | OP only |
| `/chatr info` | `chatr.admin` | OP only |
| `/chatr stats` | `chatr.admin` | OP only |
| `/chatr cache` | `chatr.admin` | OP only |
| `/chatr serverai` | `chatr.admin` | OP only |
| `/chatr <npc> ai` | `chatr.admin` | OP only |
| `/chatr r` | `chatr.ai` | Everyone (if granted) |
| `/chatr clear` | `chatr.ai` | Everyone (if granted) |

## Rate Limiting

Commands are subject to rate limiting based on user permissions:

- **Default**: 10 requests per minute
- **VIP** (`chatr.ratelimit.vip`): 20 requests per minute
- **Premium** (`chatr.ratelimit.premium`): 30 requests per minute
- **Unlimited** (`chatr.ratelimit.unlimited`): No limits

## Command Completion

Chatr supports tab completion for:
- NPC names in relevant commands
- Subcommands and options
- Player names for skin commands

## Error Messages

Common error messages and solutions:

### "You don't have permission to use this command"
- You lack the required permission
- Ask a server administrator for access

### "NPC 'name' not found"
- Check the NPC name spelling (NPC names are case-sensitive)
- Ensure an NPC with that name exists under `plugins/Chatr/npcs/`
- Use the exact name as specified when creating the NPC

### "Rate limit exceeded"
- You've made too many requests recently
- Wait before trying again, or ask for higher rate limits

### "AI service unavailable"
- AI provider is not configured or unreachable
- Check server configuration and API keys

## See Also

- [[Permissions|Permission System]]
- [[NPCs|NPC Management]]
- [[Troubleshooting|Command Issues]]


