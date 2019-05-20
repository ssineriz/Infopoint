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
		Vector<InfopointRetriever> ir = new Vector<InfopointRetriever>();
		Vector<TenarisTodayRetriever> tt = new Vector<TenarisTodayRetriever>();
		Vector<FacebookRetriever> fb = new Vector<FacebookRetriever>();		
		Vector<TwitterRetriever> tw = new Vector<TwitterRetriever>();
		Vector<InstagramRetriever> ig = new Vector<InstagramRetriever>();

			
    	for(int i =0; i<channels.size(); i++){
    		JSONObject channel = (JSONObject) channels.get(i);
    		JSONObject channelType = (JSONObject) channel.get("ChannelType");
    		switch(Integer.parseInt(channelType.get("TypeId").toString())){
    			case 6:
    				ir.add(new InfopointRetriever(channel.get("ChannelUrl").toString(), channel.get("Id").toString(), channel.get("ChannelName").toString()) );
    				break;
    			case 1:
    				tt.add(new TenarisTodayRetriever(channel.get("ChannelUrl").toString(), channel.get("Id").toString(), channel.get("ChannelName").toString()) );
    				break;
    			case 2:
    				fb.add(new FacebookRetriever(channel.get("ChannelUrl").toString(), channel.get("Id").toString(), channel.get("ChannelName").toString()) );
    				break;
    			case 3:
    				tw.add(new TwitterRetriever(channel.get("ChannelUrl").toString(), channel.get("Id").toString(), channel.get("ChannelName").toString()) );
    				break;
    			case 4:
    				ig.add(new InstagramRetriever(channel.get("ChannelUrl").toString(), channel.get("Id").toString(), channel.get("ChannelName").toString()) );
    				break;
    				/*
    			case 5:
    				tt.add(new YoutubeRetriever(channel.get("ChannelUrl").toString(), channel.get("ChannelName").toString()) );
    				break;*/    				
    		}
    	
    	}

    	for(InfopointRetriever r: ir){
			log.info("Running Infopoint channel " + r.channelName);
			try{
				r.execute(ctx);
			} catch (Exception ex){
				log.error("Infopoint Retriever", ex);
			}
		}
		for(TenarisTodayRetriever r: tt){
			log.info("Running TenarisToday channel " + r.channelName);
			try{
				r.execute(ctx);
			} catch (Exception ex){
				log.error("TenarisToday Retriever", ex);
			}
		}
		for(FacebookRetriever r: fb){
			log.info("Running Facebook channel " + r.channelName);
			try{
				r.execute(ctx);
			} catch (Exception ex){
				log.error("Facebook Retriever", ex);
			}
		}
		for(TwitterRetriever r: tw){
			log.info("Running Twitter channel " + r.channelName);
			try{
				r.execute(ctx);
			} catch (Exception ex){
				log.error("Twitter Retriever", ex);
			}
		}
		for(InstagramRetriever r: ig){
			log.info("Running Instagram channel " + r.channelName);
			try{
				r.execute(ctx);
			} catch (Exception ex){
				log.error("Instagram Retriever", ex);
			}
		}
		
	}
}
