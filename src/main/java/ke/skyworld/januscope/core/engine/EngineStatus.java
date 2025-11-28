package ke.skyworld.januscope.core.engine;

/**
 * Engine lifecycle status
 */
public enum EngineStatus {
    UNINITIALIZED,
    INITIALIZING,
    INITIALIZED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
