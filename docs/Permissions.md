# Permissions

Permission system for controlling access to Chatr features.

## Overview

Chatr uses a simple permission system that integrates with your server's permission plugin (LuckPerms, PermissionsEx, etc.).

## Available Permissions

### Basic Usage
```
chatr.use
```
- Allows use of basic /chatr commands
- Default: true (all players)

### Administration
```
chatr.admin
```
- Full plugin access including direct NPC chat
- Default: op

### NPC Management
```
chatr.create
```
- Create new NPCs
- Default: op

```
chatr.remove
```
- Remove NPCs
- Default: op

```
chatr.skin
```
- Set NPC skins
- Default: op

```
chatr.color
```
- Set NPC interaction message colors
- Default: op

### Configuration
```
chatr.reload
```
- Reload plugin configuration
- Default: op

### Player Chat
```
chatr.ai
```
- Chat with NPCs using `/chatr r` and clear conversations
- Default: not set (grant to players who should chat with NPCs)

### Administration
```
chatr.admin
```
- Full administrative access (stats, cache, serverai commands, info, direct NPC chat)
- Default: op

## Permission Setup

### LuckPerms Example
```bash
# Basic player permissions
lp group default permission set chatr.use true

# Admin permissions
lp group admin permission set chatr.create true
lp group admin permission set chatr.remove true
lp group admin permission set chatr.skin true
lp group admin permission set chatr.reload true
```

### PermissionsEx Example
```yaml
groups:
  default:
    permissions:
    - chatr.use
  admin:
    permissions:
    - chatr.create
    - chatr.remove
    - chatr.skin
    - chatr.reload
```

## Command Permissions

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
| `/chatr <npc> ai` | `chatr.admin` | OP only |
| `/chatr r` | `chatr.ai` | Everyone (if granted) |
| `/chatr clear` | `chatr.ai` | Everyone (if granted) |
| `/chatr stats` | `chatr.admin` | OP only |
| `/chatr cache` | `chatr.admin` | OP only |
| `/chatr serverai` | `chatr.admin` | OP only |

## Notes

- All permissions default to OP-only except `chatr.use` (and any changes you make with a permission plugin)
- Rate limiting permissions:
  - `chatr.ratelimit.vip` – higher rate limits for VIP players
  - `chatr.ratelimit.premium` – premium rate limits
  - `chatr.ratelimit.unlimited` – no rate limits
  - `chatr.ratelimit.bypass` – bypass rate limiting entirely
- The permission system is intentionally simple; most administrative features are gated behind `chatr.admin`
- Future versions may add more granular permissions
