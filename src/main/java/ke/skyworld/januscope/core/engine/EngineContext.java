package ke.skyworld.januscope.core.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared context for all engines.
 * Engines can share data and reference each other through this context.
 */
public class EngineContext {
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, Engine> engines = new ConcurrentHashMap<>();
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        return (T) attributes.get(key);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public void registerEngine(String name, Engine engine) {
        engines.put(name, engine);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Engine> T getEngine(String name, Class<T> type) {
        return (T) engines.get(name);
    }
    
    public Engine getEngine(String name) {
        return engines.get(name);
    }
}
