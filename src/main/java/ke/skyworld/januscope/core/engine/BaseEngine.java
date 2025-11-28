package ke.skyworld.januscope.core.engine;

import ke.skyworld.januscope.utils.Logger;

/**
 * Abstract base implementation of Engine interface.
 * Provides common functionality for all engines.
 */
public abstract class BaseEngine implements Engine {
    protected final Logger logger;
    protected EngineStatus status = EngineStatus.UNINITIALIZED;
    protected EngineContext context;
    
    protected BaseEngine() {
        this.logger = Logger.getLogger(getClass());
    }
    
    @Override
    public void initialize(EngineContext context) throws EngineException {
        logger.info("Initializing {} engine...", getName());
        this.status = EngineStatus.INITIALIZING;
        this.context = context;
        
        try {
            doInitialize();
            this.status = EngineStatus.INITIALIZED;
            logger.info("{} engine initialized successfully", getName());
        } catch (Exception e) {
            this.status = EngineStatus.FAILED;
            logger.error("{} engine initialization failed", getName(), e);
            throw new EngineException("Failed to initialize " + getName() + " engine", e);
        }
    }
    
    @Override
    public void start() throws EngineException {
        logger.info("Starting {} engine...", getName());
        this.status = EngineStatus.STARTING;
        
        try {
            doStart();
            this.status = EngineStatus.RUNNING;
            logger.info("{} engine started successfully", getName());
        } catch (Exception e) {
            this.status = EngineStatus.FAILED;
            logger.error("{} engine start failed", getName(), e);
            throw new EngineException("Failed to start " + getName() + " engine", e);
        }
    }
    
    @Override
    public void stop() throws EngineException {
        logger.info("Stopping {} engine...", getName());
        this.status = EngineStatus.STOPPING;
        
        try {
            doStop();
            this.status = EngineStatus.STOPPED;
            logger.info("{} engine stopped successfully", getName());
        } catch (Exception e) {
            this.status = EngineStatus.FAILED;
            logger.error("{} engine stop failed", getName(), e);
            throw new EngineException("Failed to stop " + getName() + " engine", e);
        }
    }
    
    @Override
    public EngineStatus getStatus() {
        return status;
    }
    
    /**
     * Subclasses implement their initialization logic here
     */
    protected abstract void doInitialize() throws Exception;
    
    /**
     * Subclasses implement their start logic here
     */
    protected abstract void doStart() throws Exception;
    
    /**
     * Subclasses implement their stop logic here
     */
    protected abstract void doStop() throws Exception;
}
