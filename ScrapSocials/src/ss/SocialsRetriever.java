package ss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class SocialsRetriever {
	private final Log log = LogFactory.getLog(SocialsRetriever.class);
	public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {

		DefaultHttpClient client = new DefaultHttpClient();
    	client.getCredentialsProvider().setCredentials(AuthScope.ANY, new NTCredentials("AdmServ_InfoPoint", "Info2015Point", "", "DALMINE"));

    	// Retrieve "devices" list and populate mongo collection Devices
    	HttpGet request = new HttpGet("http://teamwork.tenaris.net/sites/comm_com_europe/Infopoint/_vti_bin/listdata.svc/" + 
    	"Channels?$select=Id,ChannelName,ChannelUrl,ChannelType&$expand=ChannelType");
    	request.addHeader("accept", "application/json");
    	HttpResponse response = client.execute(request);
    	StatusLine status =  response.getStatusLine();
    	if(status.getStatusCode()!=HttpStatus.SC_OK){
    		// log error
    		log.error("Http response " + status.getStatusCode() + ":" + status.getReasonPhrase() + " retrieving channels");
    		return;
    	}

    	BufferedReader rd = new BufferedReader (new InputStreamReader(response.getEntity().getContent(), Charset.forName("UTF-8").newDecoder()));
    	JSONObject json = (JSONObject) new JSONParser().parse(rd);

    	JSONObject jsonResp = (JSONObject)json.get("d");
    	if(jsonResp==null){
    		log.error("Json returned " + rd + " retrieving channels");
    		// continue
    		return;
    	}
    	JSONArray channels = (JSONArray)jsonResp.get("results");
    	Vector<ChannelRetriever> retrievers = new Vector<ChannelRetriever>();
    	for(int i =0; i<channels.size(); i++){
    		JSONObject channel = (JSONObject) channels.get(i);
    		JSONObject channelType = (JSONObject) channel.get("ChannelType");
    		switch(Integer.parseInt(channelType.get("TypeId").toString())){
    			case 6:
    				retrievers.add(new InfopointRetriever(channel));
    				break;
    			case 1:
    				// http://ggregator vs. http://tenaristoday
    				if(channel.get("ChannelUrl").toString().startsWith("http://tenaristoday")) {
    					retrievers.add(new TenarisTodayRssRetriever(channel));
    				} else {
    					retrievers.add(new TenarisTodayRetriever(channel));
    				}
    				break;
    			case 2:
    				retrievers.add(new FacebookRetriever(channel));
    				break;
    			case 3:
    				retrievers.add(new TwitterRetriever(channel));
    				break;
    			case 4:
    				retrievers.add(new InstagramRetriever(channel));
    				break;
    				/*
    			case 5:
    				retrievers.add(new YoutubeRetriever(channel.get("ChannelUrl").toString(), channel.get("ChannelName").toString()) );
    				break;
    				*/
    		}
    	
    	}
    	
    	for(ChannelRetriever r: retrievers){
			log.info("Running " + r.channelType + " channel " + r.channelName);
			try{
				r.execute(ctx);
			} catch (Exception ex){
				log.error(r.channelType + " Retriever", ex);
			}
		}
	}
}
