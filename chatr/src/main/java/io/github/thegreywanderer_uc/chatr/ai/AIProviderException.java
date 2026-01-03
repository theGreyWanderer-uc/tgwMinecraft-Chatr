package io.github.thegreywanderer_uc.chatr.ai;

/**
 * Exception thrown by AI providers when API calls fail
 */
public class AIProviderException extends Exception {
    
    public AIProviderException(String message) {
        super(message);
    }
    
    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}