package ke.skyworld.januscope.core.engine;

/**
 * Base interface for all Januscope engines.
 * Every major component (Configuration, Security, Database, Monitoring, etc.) implements this.
 */
public interface Engine {
    /**
     * Initialize the engine with configuration
     */
    void initialize(EngineContext context) throws EngineException;
    
    /**
     * Start the engine
     */
    void start() throws EngineException;
    
    /**
     * Stop the engine gracefully
     */
    void stop() throws EngineException;
    
    /**
     * Get engine name
     */
    String getName();
    
    /**
     * Get engine status
     */
    EngineStatus getStatus();
    
    /**
     * Health check
     */
    boolean isHealthy();
}
