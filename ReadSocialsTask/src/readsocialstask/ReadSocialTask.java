/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package readsocialstask;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.startup.tasks.MessageInjector;

/**
 *
 * @author Jacques
 */
public class ReadSocialTask extends MessageInjector {
    
    private final Log log = LogFactory.getLog(ReadSocialTask.class);
    private SynapseEnvironment synapseEnvironment;
 
    @Override
    public void execute() {
        log.debug("ReadSocialTask begin");

        if (synapseEnvironment == null) {
            log.error("Synapse Environment not set");
            return;    
        }

        try {
            new SocialsRetriever().Execute();

        } catch (Exception e) {
            throw new SynapseException("Some Error has occurred", e);
        }
        log.debug("ReadSocialTask end");  
    }
 
    @Override
    public void destroy() {
    }

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }
 
    public SynapseEnvironment getSynapseEnvironment() {
        return synapseEnvironment;
    }

    public void setSynapseEnvironment(SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }
 
}
