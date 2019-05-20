package ss;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;

public class WatchdogTask implements org.apache.synapse.task.Task {

    private final Log log = LogFactory.getLog(WatchdogTask.class);
    private SynapseEnvironment synapseEnvironment;
    private String configParams; 
 
    public void execute() {
        log.debug("WatchdogTask begin");
        /*
        if (synapseEnvironment == null) {
            log.error("Synapse Environment not set");
            return;    
        }
		*/
        
        try {
            new Watchdog().CheckNodes();
        } catch (Exception e) {
            throw new SynapseException("Some Error has occurred", e);
        }
        log.debug("WatchdogTask end");  
    }
 
    public void destroy() {
    }

    public void init(SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }
 
    public SynapseEnvironment getSynapseEnvironment() {
        return synapseEnvironment;
    }

    public void setSynapseEnvironment(SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }
    
    public String getConfigParams(){
    	return configParams;
    }
    
    public void setConfigParams(String configParams){
    	this.configParams = configParams;
    }
 
}
