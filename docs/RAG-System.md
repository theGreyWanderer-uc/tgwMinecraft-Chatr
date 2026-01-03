# RAG System

The Retrieval-Augmented Generation (RAG) system provides accurate, up-to-date Minecraft information by pulling from a knowledge base.

## What is RAG?

RAG combines:
- **Retrieval**: Finding relevant information from a knowledge base
- **Generation**: Using that information to create accurate responses

This ensures the ServerAI provides factual, current Minecraft information. (RAG is currently wired to ServerAI only.)

## Requirements

### LM Studio Setup
RAG requires LM Studio with a specific embedding model:

1. **Download LM Studio**: Get it from [lmstudio.ai](https://lmstudio.ai)
2. **Install Embedding Model**: Search for and download "nomic-ai/nomic-embed-text-v1.5-GGUF"
3. **Alternative Source**: Download from [HuggingFace](https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF)
4. **Load Model**: Start LM Studio and load the embedding model
5. **Start Server**: Ensure LM Studio server is running

### Configuration
Enable RAG in `config.yml`:

```yaml
server-ai:
  rag:
    enabled: true
    dataPath: "ragData"
    embeddingModel: "nomic-ai/nomic-embed-text-v1.5"
    lmStudioUrl: "http://localhost:1234"
    maxContextLength: 2000
    topK: 5
    maxHops: 1
    similarityThreshold: 0.5
    maxTotalDocs: 15
    maxContextDocs: 5
    snippetWindow: 600
    fallbackPrefixLen: 800
```

## How RAG Works

### Knowledge Base Creation
1. **Data Collection**: Gather Minecraft information (wiki pages, guides, etc.)
2. **Text Processing**: Split content into manageable chunks
3. **Embedding Generation**: Convert text to numerical vectors
4. **Storage**: Save embeddings in the `ragData` directory

### Query Processing
1. **Question Analysis**: User asks a question
2. **Similarity Search**: Find relevant knowledge chunks
3. **Context Assembly**: Combine relevant information
4. **Response Generation**: AI uses context to answer accurately

## Configuration Options

### Basic Settings
- `enabled`: Turn RAG on/off
- `dataPath`: Directory for knowledge base storage
- `lmStudioUrl`: LM Studio server URL

### Embedding Model
- `embeddingModel`: Must be "nomic-ai/nomic-embed-text-v1.5"
- Requires LM Studio to host the model

### Search Parameters
- `topK`: Initial documents to retrieve (default: 5)
- `maxHops`: Graph expansion depth (default: 1)
- `similarityThreshold`: Minimum similarity for expansion (0.0-1.0)
- `maxTotalDocs`: Maximum documents after expansion (default: 15)
- `maxContextDocs`: Documents included in final context (default: 5)

### Content Processing
- `snippetWindow`: Characters around matched phrases (default: 600)
- `fallbackPrefixLen`: Characters to include if no match (default: 800)
- `maxContextLength`: Maximum context length (default: 2000)

## Setting Up Knowledge Base

### Downloading Pre-built Knowledge Base
A pre-built knowledge base is available for download:

1. **Download the ZIP**: Get `ragData.zip` directly from the repository at `ragData/ragData.zip`.
2. **Extract**: Unzip the file into your `plugins/Chatr/` folder.
3. **Verify**: Ensure the `ragData` folder contains the required files (see File Structure below).

This provides a ready-to-use knowledge base with current Minecraft information.

### Manual Data Addition (Advanced)
For custom knowledge bases, you can manually populate the data. This requires Python and LM Studio setup. Future versions may include automated data collection.

### Data Format
Knowledge base expects text documents containing Minecraft information:
- Crafting recipes
- Block/item information
- Game mechanics
- Update changelogs
- Community guides

### File Structure
```
plugins/Chatr/ragData/
├── docs.json           # Document text and metadata
├── embeddings.npy      # Numpy array of document embeddings
├── faiss_index.index   # FAISS similarity search index
├── graph.json          # Document relationship graph
└── *.py                # Python scripts for building/reranking (optional)
```

## Performance Considerations

### Resource Usage
- Embedding generation requires GPU/CPU resources
- Storage space for knowledge base
- Memory for context processing

### API Costs
- RAG reduces API calls by providing accurate information
- Local embedding model (no cloud costs)
- Only generation uses configured AI provider

### Response Quality
- More accurate Minecraft information
- Reduced hallucinations
- Context-aware responses

## Troubleshooting

### Model Loading Issues
- Ensure nomic-ai model is downloaded and loaded in LM Studio
- Check LM Studio server is running on correct port
- Verify model name matches exactly

### No Context Retrieved
- Check knowledge base has been populated
- Verify similarity threshold isn't too high
- Test with simpler queries first

### Poor Response Quality
- Ensure knowledge base contains relevant information
- Adjust `maxContextDocs` for more/less context
- Check AI provider configuration

### Performance Issues
- Lower `topK` and `maxTotalDocs` for faster searches
- Increase `similarityThreshold` to reduce noise
- Monitor LM Studio resource usage

## Best Practices

### Knowledge Base Management
- Regularly update with new Minecraft information
- Include diverse sources (official wiki, community guides)
- Test knowledge base with common questions

### Configuration Tuning
- Start with default settings
- Adjust based on server performance
- Monitor response quality and speed

### Content Quality
- Use accurate, up-to-date information
- Include examples and edge cases
- Organize content by topic

## Future Enhancements

Planned improvements:
- Automated knowledge base population
- Web scraping for current information
- Multiple embedding model support
- Knowledge base management tools

## See Also

- [[Configuration|RAG Configuration]]
- [[API-Providers|AI Provider Setup]]
- [[ServerAI|ServerAI Integration]]
- [[Troubleshooting|RAG Issues]]