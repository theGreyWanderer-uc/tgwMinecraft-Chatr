package io.github.thegreywanderer_uc.chatr;

// import com.criteo.jfaiss.Index;
// import com.criteo.jfaiss.IndexFlatIP;
// import com.criteo.jfaiss.IndexIVFFlat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) system for ServerAI
 * Provides semantic search and context retrieval from knowledge base
 */
public class RAGSystem {
    private static final Logger logger = Logger.getLogger(RAGSystem.class.getName());
    
    private float[][] embeddings;
    private final List<String> documents;
    private final Map<Integer, List<GraphEdge>> knowledgeGraph;
    private final Gson gson = new Gson();
    private final int topK;
    private final int maxHops;
    private final int maxContextLength;
    private final String lmStudioUrl;
    private final float similarityThreshold;
    private final int maxTotalDocs;
    private final int maxContextDocs;
    private final int snippetWindow;
    private final int fallbackPrefixLen;

    public RAGSystem(File dataDir, int topK, int maxHops, int maxContextLength, String lmStudioUrl, 
                    float similarityThreshold, int maxTotalDocs, int maxContextDocs, int snippetWindow, int fallbackPrefixLen) throws IOException {
        this.topK = topK;
        this.maxHops = maxHops;
        this.maxContextLength = maxContextLength;
        this.lmStudioUrl = lmStudioUrl;
        this.similarityThreshold = similarityThreshold;
        this.maxTotalDocs = maxTotalDocs;
        this.maxContextDocs = maxContextDocs;
        this.snippetWindow = snippetWindow;
        this.fallbackPrefixLen = fallbackPrefixLen;

        logger.info("[RAG] Initializing RAG system from data directory: " + dataDir.getAbsolutePath());
        if (!dataDir.exists()) {
            throw new IOException("RAG data directory does not exist: " + dataDir.getAbsolutePath());
        }
        if (!dataDir.isDirectory()) {
            throw new IOException("RAG data path is not a directory: " + dataDir.getAbsolutePath());
        }
        File[] files = dataDir.listFiles();
        if (files != null) {
            logger.info("[RAG] Found " + files.length + " files in data directory: " + 
                       Arrays.toString(Arrays.stream(files).map(File::getName).toArray()));
        } else {
            logger.warning("[RAG] Could not list files in data directory");
        }

        // Load documents
        File docsFile = new File(dataDir, "docs.json");
        if (!docsFile.exists()) {
            throw new IOException("Documents file not found: " + docsFile.getPath());
        }
        
        List<String> tempDocuments = null;
        try (FileReader reader = new FileReader(docsFile)) {
            // Try to parse as List<String> first
            try {
                tempDocuments = gson.fromJson(reader, new TypeToken<List<String>>(){}.getType());
            } catch (Exception e) {
                // If that fails, try to parse as List<Map<String, Object>> (common RAG format)
                reader.close();
                try (FileReader reader2 = new FileReader(docsFile)) {
                    List<Map<String, Object>> docObjects = gson.fromJson(reader2,
                        new TypeToken<List<Map<String, Object>>>(){}.getType());

                    // Extract text content from objects (try common field names)
                    tempDocuments = new ArrayList<>();
                    for (Map<String, Object> docObj : docObjects) {
                        String text = null;
                        // Try common field names for document content
                        if (docObj.containsKey("content")) {
                            text = docObj.get("content").toString();
                        } else if (docObj.containsKey("text")) {
                            text = docObj.get("text").toString();
                        } else if (docObj.containsKey("document")) {
                            text = docObj.get("document").toString();
                        } else if (docObj.containsKey("body")) {
                            text = docObj.get("body").toString();
                        } else {
                            // If no standard field found, convert the whole object to string
                            text = gson.toJson(docObj);
                        }
                        tempDocuments.add(text);
                    }
                    logger.info("[RAG] Parsed docs.json as objects, extracted " + tempDocuments.size() + " documents");
                } catch (Exception e2) {
                    // If that also fails, provide detailed error
                    reader.close();
                    try (FileReader reader3 = new FileReader(docsFile)) {
                        String content = new String(Files.readAllBytes(docsFile.toPath()));
                        if (content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }
                        throw new IOException("Failed to parse docs.json. Expected List<String> or List<Object>. First 500 chars: " + content, e2);
                    }
                }
            }
        }
        
        this.documents = tempDocuments;

        // Load knowledge graph
        File graphFile = new File(dataDir, "graph.json");
        if (!graphFile.exists()) {
            throw new IOException("Knowledge graph file not found: " + graphFile.getPath());
        }
        try (FileReader reader = new FileReader(graphFile)) {
            // First parse as Map<String, List<List<Object>>> to handle the JSON structure
            Map<String, List<List<Object>>> rawGraph = gson.fromJson(reader,
                new TypeToken<Map<String, List<List<Object>>>>(){}.getType());
            
            // Convert to the expected format
            this.knowledgeGraph = new HashMap<>();
            for (Map.Entry<String, List<List<Object>>> entry : rawGraph.entrySet()) {
                int key = Integer.parseInt(entry.getKey());
                List<GraphEdge> edges = new ArrayList<>();
                for (List<Object> edgeData : entry.getValue()) {
                    if (edgeData.size() >= 2) {
                        int target = ((Number) edgeData.get(0)).intValue();
                        double weight = ((Number) edgeData.get(1)).doubleValue();
                        edges.add(new GraphEdge(target, weight));
                    }
                }
                this.knowledgeGraph.put(key, edges);
            }
        }

        // Try to load embeddings (may fail if format is unexpected)
        try {
            File embeddingsFile = new File(dataDir, "embeddings.npy");
            if (embeddingsFile.exists()) {
                logger.info("[RAG] Found embeddings file: " + embeddingsFile.getPath() + " (size: " + embeddingsFile.length() + " bytes)");
                this.embeddings = loadNpyEmbeddings(embeddingsFile);
                logger.info("[RAG] Loaded " + embeddings.length + " embeddings from: " + embeddingsFile.getPath());
            } else {
                logger.warning("[RAG] Embeddings file not found, using keyword search only: " + embeddingsFile.getPath());
                this.embeddings = null;
            }
        } catch (Exception e) {
            logger.warning("[RAG] Failed to load embeddings from " + new File(dataDir, "embeddings.npy").getPath() + ": " + 
                         (e.getMessage() != null ? e.getMessage() : "Unknown error") + " - falling back to keyword search");
            logger.warning("[RAG] Exception details: " + e.getClass().getSimpleName() + " - " + e.toString());
            this.embeddings = null;
        }
    }

    /**
     * Retrieve relevant context for a query using vector search with knowledge graph expansion
     */
    public String retrieveContext(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }

        // Check if embeddings are available
        if (embeddings == null) {
            return retrieveContextKeywordFallback(query);
        }

        try {
            // Generate embedding for the query using LM Studio
            float[] queryVector = generateEmbedding(query);

            // Search embeddings using cosine similarity
            List<Integer> topDocIds = searchEmbeddings(queryVector, query);

            // Expand with knowledge graph
            Set<Integer> expandedDocIds = expandWithGraph(new HashSet<>(topDocIds), maxHops, queryVector);

            // Rerank expanded documents with phrase matching and re-scoring
            List<Integer> rerankedDocIds = rerankDocuments(expandedDocIds, queryVector, query);

            // Build and return context with snippets
            String context = buildContextSnippets(rerankedDocIds, query);
            logger.info("[RAG] Built context for query '" + query + "': " + context.length() + " chars from " + rerankedDocIds.size() + " documents");
            return context;
        } catch (Exception e) {
            // Fallback to keyword search if vector search fails
            logger.warning("[RAG] Vector search failed: " + e.getMessage() + " - falling back to keyword search");
            return retrieveContextKeywordFallback(query);
        }
    }

    /**
     * Load embeddings from .npy file (simplified reader for float32 arrays)
     */
    private float[][] loadNpyEmbeddings(File file) throws IOException {
        logger.info("[RAG] Attempting to load .npy file: " + file.getAbsolutePath() + " (size: " + file.length() + " bytes)");
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            // Read magic string - try different possible formats
            byte[] magic = new byte[6];
            dis.readFully(magic);
            
            String magicStr = new String(magic);
            boolean isValidNpy = magicStr.equals("\u0093NUMPY") || 
                                magicStr.startsWith("\u0093") || 
                                magicStr.contains("NUMPY");
            
            if (!isValidNpy) {
                // Try reading as raw bytes to see what we have
                logger.warning("[RAG] Magic string check failed. Expected \\u0093NUMPY, got: " + 
                              Arrays.toString(magic) + " (string: '" + magicStr + "')");
                throw new IOException("Not a valid .npy file - magic string mismatch");
            }

            // Skip version
            dis.skipBytes(2);

            // Read header length (little-endian short)
            byte[] headerLenBytes = new byte[2];
            dis.readFully(headerLenBytes);
            ByteBuffer headerLenBuffer = ByteBuffer.wrap(headerLenBytes).order(ByteOrder.LITTLE_ENDIAN);
            int headerLen = headerLenBuffer.getShort() & 0xFFFF; // Make sure it's positive
            logger.info("[RAG] Header length: " + headerLen + " bytes");

            // Read header
            byte[] headerBytes = new byte[headerLen];
            dis.readFully(headerBytes);
            String header = new String(headerBytes);
            logger.info("[RAG] Header content: " + header.replace("\n", "\\n"));

            // Parse shape from header (simplified - assumes 2D float32 array)
            int[] shape = parseNpyShape(header);
            logger.info("[RAG] Parsed shape: " + Arrays.toString(shape));

            // Read data
            int totalElements = shape[0] * shape[1];
            float[][] embeddings = new float[shape[0]][shape[1]];

            // Read all floats at once for better performance
            byte[] dataBytes = new byte[totalElements * 4];
            dis.readFully(dataBytes);

            ByteBuffer buffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
            FloatBuffer floatBuffer = buffer.asFloatBuffer();

            for (int i = 0; i < shape[0]; i++) {
                for (int j = 0; j < shape[1]; j++) {
                    embeddings[i][j] = floatBuffer.get(i * shape[1] + j);
                }
            }

            return embeddings;
        }
    }

    /**
     * Parse shape from .npy header (simplified)
     */
    private int[] parseNpyShape(String header) {
        // Extract shape from header like "{'shape': (100, 768), 'dtype': '<f4'}"
        int shapeStart = header.indexOf("'shape': (") + 10;
        int shapeEnd = header.indexOf(")", shapeStart);
        String shapeStr = header.substring(shapeStart, shapeEnd);

        String[] dims = shapeStr.split(",");
        int[] shape = new int[dims.length];
        for (int i = 0; i < dims.length; i++) {
            shape[i] = Integer.parseInt(dims[i].trim());
        }
        return shape;
    }
    private float[] generateEmbedding(String text) throws IOException {
        try {
            // Create HTTP client
            HttpURLConnection connection = (HttpURLConnection) new URL(lmStudioUrl + "/v1/embeddings").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", text);
            requestBody.put("model", "nomic-ai/nomic-embed-text-v1.5");

            // Send request
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                gson.toJson(requestBody, writer);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("LM Studio API returned error code: " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                Map<String, Object> response = gson.fromJson(reader, Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (data == null || data.isEmpty()) {
                    throw new IOException("No embedding data in response");
                }

                @SuppressWarnings("unchecked")
                List<Double> embeddingList = (List<Double>) data.get(0).get("embedding");
                float[] embedding = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i).floatValue();
                }
                return embedding;
            }
        } catch (Exception e) {
            throw new IOException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Search embeddings using cosine similarity
     */
    private List<Integer> searchEmbeddings(float[] queryVector, String query) {
        if (embeddings == null) {
            throw new IllegalStateException("Embeddings not loaded");
        }

        List<ScoredDocument> scoredDocs = new ArrayList<>();

        for (int i = 0; i < embeddings.length; i++) {
            float similarity = cosineSimilarity(queryVector, embeddings[i]);
            scoredDocs.add(new ScoredDocument(i, similarity));
        }

        // Sort by similarity (cosine similarity, higher is better)
        scoredDocs.sort((a, b) -> Float.compare(b.score, a.score));

        // Log top results for debugging
        logger.info("[RAG] Top " + Math.min(5, scoredDocs.size()) + " document similarities for query: " + query);
        for (int i = 0; i < Math.min(5, scoredDocs.size()); i++) {
            ScoredDocument sd = scoredDocs.get(i);
            String preview = documents.get(sd.docId).length() > 100 ? 
                documents.get(sd.docId).substring(0, 100) + "..." : 
                documents.get(sd.docId);
            logger.info(String.format("[RAG] #%d: Score=%.4f, Doc=%s", i+1, sd.score, preview.replace("\n", " ")));
        }

        return scoredDocs.stream()
            .limit(topK)
            .map(sd -> sd.docId)
            .collect(Collectors.toList());
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0f || normB == 0.0f) {
            return 0.0f;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Fallback keyword-based search
     */
    private String retrieveContextKeywordFallback(String query) throws IOException {
        // Extract keywords and phrases from query
        Set<String> keywords = extractKeywords(query.toLowerCase());
        Set<String> phrases = extractPhrases(query.toLowerCase());

        // Score documents based on keyword and phrase matches
        List<ScoredDocument> scoredDocs = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            String doc = documents.get(i).toLowerCase();
            float score = calculateKeywordScore(doc, keywords, phrases);
            if (score > 0) {
                scoredDocs.add(new ScoredDocument(i, score));
            }
        }

        // Sort by score descending and take top results
        scoredDocs.sort((a, b) -> Float.compare(b.score, a.score));
        List<Integer> topDocIds = scoredDocs.stream()
            .limit(topK)
            .map(sd -> sd.docId)
            .collect(Collectors.toList());

        // Expand with knowledge graph
        Set<Integer> expandedDocIds = expandWithGraph(new HashSet<>(topDocIds), maxHops, null);

        // Build and return context
        return buildContext(new ArrayList<>(expandedDocIds));
    }

    /**
     * Extract keywords from query (simple implementation)
     */
    private Set<String> extractKeywords(String query) {
        Set<String> keywords = new HashSet<>();
        String[] words = query.split("\\s+");

        // Common stop words to ignore
        Set<String> stopWords = Set.of("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "must", "can", "how", "what", "when", "where", "why", "who", "which");

        for (String word : words) {
            word = word.replaceAll("[^a-zA-Z]", ""); // Remove non-letters
            if (word.length() > 2 && !stopWords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Extract meaningful phrases from query
     */
    private Set<String> extractPhrases(String query) {
        Set<String> phrases = new HashSet<>();
        
        // Extract 2-3 word phrases
        String[] words = query.split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            // 2-word phrases
            if (i < words.length - 1) {
                String phrase = words[i] + " " + words[i + 1];
                if (phrase.length() > 4) { // Avoid very short phrases
                    phrases.add(phrase);
                }
            }
            // 3-word phrases
            if (i < words.length - 2) {
                String phrase = words[i] + " " + words[i + 1] + " " + words[i + 2];
                if (phrase.length() > 6) {
                    phrases.add(phrase);
                }
            }
        }
        
        return phrases;
    }

    /**
     * Calculate keyword matching score for a document
     */
    private float calculateKeywordScore(String doc, Set<String> keywords, Set<String> phrases) {
        float score = 0;
        
        // Score individual keywords
        for (String keyword : keywords) {
            int count = countOccurrences(doc, keyword);
            if (count > 0) {
                score += count * (keyword.length() * 0.1f); // Weight by keyword length
            }
        }
        
        // Score phrases (much higher weight for exact phrase matches)
        for (String phrase : phrases) {
            if (doc.contains(phrase)) {
                score += phrase.length() * 2.0f; // Much higher weight for phrases
            }
        }
        
        return score;
    }

    /**
     * Count occurrences of a substring in text
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Simple data class for scored documents
     */
    private static class ScoredDocument {
        final int docId;
        final float score;

        ScoredDocument(int docId, float score) {
            this.docId = docId;
            this.score = score;
        }
    }

    /**
     * Expand search results using knowledge graph
     */
    private Set<Integer> expandWithGraph(Set<Integer> nodeIds, int maxHops, float[] queryVector) {
        Set<Integer> expanded = new HashSet<>(nodeIds);
        Set<Integer> currentLevel = new HashSet<>(nodeIds);

        for (int hop = 0; hop < maxHops; hop++) {
            Set<Integer> nextLevel = new HashSet<>();

            for (Integer nodeId : currentLevel) {
                List<GraphEdge> edges = knowledgeGraph.get(nodeId);
                if (edges != null) {
                    for (GraphEdge edge : edges) {
                        if (!expanded.contains(edge.target) && expanded.size() < maxTotalDocs) {
                            // If we have a query vector, check similarity threshold
                            if (queryVector != null && embeddings != null) {
                                float similarity = cosineSimilarity(queryVector, embeddings[edge.target]);
                                if (similarity >= similarityThreshold) {
                                    nextLevel.add(edge.target);
                                }
                            } else {
                                // No similarity filtering for keyword fallback
                                nextLevel.add(edge.target);
                            }
                        }
                    }
                }
            }

            if (nextLevel.isEmpty()) {
                break;
            }

            expanded.addAll(nextLevel);
            currentLevel = nextLevel;
            
            // Stop if we've reached the maximum total documents
            if (expanded.size() >= maxTotalDocs) {
                break;
            }
        }

        return expanded;
    }

    /**
     * Build context string from document IDs
     */
    private String buildContext(List<Integer> docIds) {
        StringBuilder context = new StringBuilder();

        for (Integer docId : docIds) {
            if (docId >= 0 && docId < documents.size()) {
                String doc = documents.get(docId);
                if (context.length() + doc.length() > maxContextLength) {
                    break; // Don't exceed context length
                }
                if (context.length() > 0) {
                    context.append("\n\n");
                }
                context.append(doc);
            }
        }

        return context.toString();
    }

    /**
     * Rerank documents by cosine similarity and phrase matching priority
     */
    private List<Integer> rerankDocuments(Set<Integer> docIds, float[] queryVector, String query) {
        // Re-score all documents by cosine similarity
        List<ScoredDocument> scoredDocs = new ArrayList<>();
        for (Integer docId : docIds) {
            float similarity = cosineSimilarity(queryVector, embeddings[docId]);
            scoredDocs.add(new ScoredDocument(docId, similarity));
        }

        // Sort by similarity (higher first)
        scoredDocs.sort((a, b) -> Float.compare(b.score, a.score));

        // Prioritize documents containing the exact query phrase
        String queryLower = query.toLowerCase().trim();
        List<Integer> phraseMatches = new ArrayList<>();
        List<Integer> nonPhraseMatches = new ArrayList<>();

        for (ScoredDocument sd : scoredDocs) {
            String docText = documents.get(sd.docId);
            if (docText != null && docText.toLowerCase().contains(queryLower)) {
                phraseMatches.add(sd.docId);
            } else {
                nonPhraseMatches.add(sd.docId);
            }
        }

        // Combine: phrase matches first, then others by similarity
        List<Integer> reranked = new ArrayList<>(phraseMatches);
        reranked.addAll(nonPhraseMatches);

        // Limit to max total docs
        if (reranked.size() > maxTotalDocs) {
            reranked = reranked.subList(0, maxTotalDocs);
        }

        return reranked;
    }

    /**
     * Build context using snippets around matched phrases
     */
    private String buildContextSnippets(List<Integer> docIds, String query) {
        StringBuilder context = new StringBuilder();
        String queryLower = query.toLowerCase().trim();

        for (int i = 0; i < Math.min(docIds.size(), maxContextDocs); i++) {
            Integer docId = docIds.get(i);
            if (docId >= 0 && docId < documents.size()) {
                String docText = documents.get(docId);
                if (docText == null) continue;

                String snippet;
                String docLower = docText.toLowerCase();

                // Check if document contains the query phrase
                if (docLower.contains(queryLower)) {
                    // Extract snippet around the matched phrase
                    int matchIndex = docLower.indexOf(queryLower);
                    int start = Math.max(0, matchIndex - snippetWindow / 2);
                    int end = Math.min(docText.length(), matchIndex + queryLower.length() + snippetWindow / 2);
                    snippet = docText.substring(start, end).trim();
                } else {
                    // Fallback: use prefix of document
                    snippet = docText.length() > fallbackPrefixLen ?
                        docText.substring(0, fallbackPrefixLen).trim() :
                        docText.trim();
                }

                // Add separator if not first document
                if (context.length() > 0) {
                    context.append("\n\n---\n\n");
                }
                context.append(snippet);

                // Check if we've exceeded context length
                if (context.length() > maxContextLength) {
                    break;
                }
            }
        }

        return context.toString();
    }

    /**
     * Get the number of documents loaded
     */
    public int getDocumentCount() {
        return documents != null ? documents.size() : 0;
    }

    /**
     * Get the number of embeddings loaded
     */
    public int getEmbeddingCount() {
        return embeddings != null ? embeddings.length : 0;
    }

    /**
     * Simple data class for graph edges
     */
    public static class GraphEdge {
        public int target;
        public double weight;
        
        public GraphEdge(int target, double weight) {
            this.target = target;
            this.weight = weight;
        }
    }
}