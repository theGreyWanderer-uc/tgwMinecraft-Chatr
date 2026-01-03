# Troubleshooting

Common issues and solutions for the Chatr plugin.

## Plugin Won't Load

### Java Version Issues
**Error**: `UnsupportedClassVersionError` or similar

**Solution**:
- Ensure Java 21 is installed
- Check `java -version` output
- Update Java if necessary

### Missing Dependencies
**Error**: Plugin fails to enable with dependency errors

**Solution**:
- Install required plugins: ProtocolLib
- Ensure compatible versions
- Check server logs for specific errors

### Configuration Errors
**Error**: YAML parsing errors on startup

**Solution**:
- Validate `config.yml` syntax
- Use online YAML validator
- Check indentation and quotes

## AI Provider Issues

### Connection Failures
**Error**: "Failed to connect to AI provider"

**Solutions**:
- Verify API key is correct
- Check internet connectivity
- Confirm provider endpoints are accessible
- Review firewall settings

### Rate Limiting
**Error**: "Rate limit exceeded"

**Solutions**:
- Reduce request frequency
- Upgrade API plan if needed
- Implement request queuing
- Use multiple API keys

### Invalid Provider Configuration
**Error**: "Unknown AI provider 'xyz' requested, falling back to local provider"

**Cause**: An invalid or unsupported provider name was specified in the configuration.

**Solutions**:
- Check `config.yml` for valid provider names
- Supported providers: `groq`, `mistral`, `nvidia`, `openrouter`, `gemini`
- For local AI: Use URLs like `http://localhost:1234` (LM Studio) or `http://localhost:11434` (Ollama)
- The plugin will automatically fall back to the local provider if an invalid name is used

### Invalid Responses
**Error**: AI returns malformed or inappropriate content

**Solutions**:
- Adjust temperature settings (lower for more consistent responses)
- Use different models
- Implement content filtering
- Review prompt templates

## NPC Issues

### NPCs Not Spawning
**Error**: NPCs don't appear in world

**Solutions**:
- Check world permissions
- Verify spawn location is loaded
- Ensure sufficient space around spawn point
- Check for conflicting plugins

### NPCs Not Responding
**Error**: NPCs ignore player interactions

**Solutions**:
- Ensure click-to-chat is enabled in `config.yml` (`click-to-chat.enabled: true`)
- Verify NPCs have a non-empty `system-prompt` in their `config.yml`
- Check that your AI provider/endpoint is reachable
- Review console for interaction errors

### NPC Data Corruption
**Error**: NPCs lose configuration or behavior

**Solutions**:
- Backup NPC data regularly
- Avoid server crashes during NPC editing
- NPCs are saved automatically when created or modified
- Check disk space and file permissions

## ServerAI Issues

### Commands Not Working
**Error**: ServerAI commands fail or produce errors

**Solutions**:
- Verify permissions (`chatr.admin`)
- Check command syntax (`/chatr serverai [status|refresh|clear]`)
- Ensure `server-ai.enabled: true` and `server-ai.server-url`/`model` are configured
- Review console for detailed errors

### Poor Response Quality
**Error**: ServerAI gives irrelevant or incorrect responses

**Solutions**:
- Refine prompt templates
- Adjust AI model settings
- Enable RAG system for better context
- Provide clearer instructions

### Performance Problems
**Error**: ServerAI causes lag or high CPU usage

**Solutions**:
- Reduce concurrent requests
- Implement rate limiting
- Use faster AI models
- Monitor resource usage

## RAG System Issues

### Embedding Model Problems
**Error**: "Failed to load embedding model"

**Solutions**:
- Ensure LM Studio is running
- Verify nomic-ai model is downloaded and loaded
- Check LM Studio server URL and port
- Restart LM Studio if needed

### No Context Retrieved
**Error**: RAG queries return no relevant information

**Solutions**:
- Populate knowledge base with relevant data
- Lower similarity threshold
- Check query formulation
- Verify embedding generation worked

### Slow Performance
**Error**: RAG searches take too long

**Solutions**:
- Reduce `topK` and `maxTotalDocs`
- Increase similarity threshold
- Optimize knowledge base size
- Use faster hardware for embeddings

## Permission Issues

### Players Can't Use Features
**Error**: Players report "no permission" errors

**Solutions**:
- Grant appropriate permissions via your permission plugin (LuckPerms, etc.)
- Check permission inheritance (e.g. groups vs users)
- Verify permission plugin compatibility

### Admin Commands Fail
**Error**: Admin commands don't work

**Solutions**:
- Ensure OP status or appropriate permissions (`chatr.admin`, `chatr.reload`, etc.)
- Check for permission conflicts
- Verify command syntax
- Review console logs

## Configuration Issues

### Settings Not Applying
**Error**: Configuration changes don't take effect

**Solutions**:
- Reload plugin after changes (`/reload` or restart)
- Check for YAML syntax errors
- Verify file encoding (UTF-8)
- Backup and restore if corrupted

### Invalid Configuration Values
**Error**: Plugin rejects configuration values

**Solutions**:
- Review documentation for valid ranges
- Use default values as reference
- Check for typos in keys/values
- Validate with online YAML tools

## Performance Issues

### High Memory Usage
**Error**: Plugin consumes excessive RAM

**Solutions**:
- Reduce NPC count
- Lower AI request frequency
- Disable unused features
- Monitor with server performance tools

### Lag Spikes
**Error**: Server experiences lag when using plugin

**Solutions**:
- Implement request queuing
- Use async processing where possible
- Reduce concurrent AI requests
- Profile with timing tools

### Database Issues
**Error**: Data corruption or slow queries

**Solutions**:
- Regular backups
- Optimize database queries
- Check disk I/O performance
- Consider database migration

## Logging and Debugging

### Enable Debug Logging
Set in `config.yml`:
```yaml
debug-mode: true
```

### Common Log Messages
- `INFO`: Normal operation
- `WARNING`: Potential issues
- `SEVERE`: Serious problems requiring attention

### Log Analysis
- Check timestamps for issue correlation
- Look for stack traces in errors
- Search for specific error codes
- Compare with successful operations

## Getting Help

### Information to Provide
When reporting issues:
- Server version and Java version
- Plugin version and other plugins
- Full error messages and stack traces
- Steps to reproduce the issue
- Configuration files (redact sensitive data)

### Support Channels
- GitHub Issues: For bug reports and feature requests
- Documentation: Check wiki for common solutions

## Prevention

### Regular Maintenance
- Keep plugins updated
- Monitor server performance
- Regular configuration backups
- Test changes on development server

### Best Practices
- Use stable plugin versions
- Configure rate limiting
- Implement proper permissions
- Monitor resource usage

## See Also

- [[Installation|Installation Guide]]
- [[Configuration|Configuration Details]]
- [[Commands|Command Reference]]
- [[API-Providers|AI Provider Setup]]