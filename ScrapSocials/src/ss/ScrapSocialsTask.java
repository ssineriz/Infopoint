package ss;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;


public class ScrapSocialsTask implements org.apache.synapse.task.Task {

    private final Log log = LogFactory.getLog(ScrapSocialsTask.class);
    private SynapseEnvironment synapseEnvironment;
    private String configParams; 
 
    public void execute() {
        log.warn("ReadSocialTask begin");
        /*
        if (synapseEnvironment == null) {
            log.error("Synapse Environment not set");
            return;    
        }
		*/
        
        try {
        	// Ensure db ok
        	Mongo mongo = new Mongo("localhost:27017");
        	DB db = mongo.getDB("infopoint");
        	db.authenticate("wso2", "wso2".toCharArray());
        	DBCollection coll = db.getCollection("infopoint");
        	BasicDBObject idx = new BasicDBObject();
        	idx.put("date", 1);
        	coll.ensureIndex(idx);
        	idx=new BasicDBObject();
        	idx.put("channelType", 1);
        	coll.ensureIndex(idx);
            new SocialsRetriever().execute(new ExecutionContext());
        } catch (Exception e) {
            throw new SynapseException("Some Error has occurred", e);
        }
        log.debug("ReadSocialTask end");  
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
