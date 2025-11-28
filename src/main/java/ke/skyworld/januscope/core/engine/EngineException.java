package ke.skyworld.januscope.core.engine;

/**
 * Base exception for all engine-related errors
 */
public class EngineException extends Exception {
    public EngineException(String message) {
        super(message);
    }
    
    public EngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
