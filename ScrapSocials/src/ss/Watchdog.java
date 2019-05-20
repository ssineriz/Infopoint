package ss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.TimeUnit;

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

class StreamGobbler extends Thread
{
    InputStream is;
    String type;
    
    StreamGobbler(InputStream is, String type) {
        this.is = is;
        this.type = type;
    }
    
    public void run() {
    	try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line=null;
            while ( (line = br.readLine()) != null)
                System.out.println(type + ">" + line);    
    	} catch (IOException ioe)  {
		    ioe.printStackTrace();  
    	}
    }
}

public class Watchdog {
	protected final Log log = LogFactory.getLog(Watchdog.class);
	protected final long MilisAdjust = 3 * 3600 * 1000;
	protected final static String StoreKey = "c:\\windows\\system32\\cmd /c echo y | d:\\appls\\Java\\plink.exe -ssh %s -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk exit";
	protected final static String Refreshcmd = "d:\\appls\\Java\\plink.exe -ssh %s -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk \"pkill midori\"";
	protected final static String Rebootcmd = "d:\\appls\\Java\\plink.exe -ssh %s -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk reboot";

	protected final static String GetThumbcmd = "d:\\appls\\Java\\plink.exe -ssh %s -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk sudo -u pi DISPLAY=:0 scrot -z -t 30 /tmp/image.png";
	protected final static String CopyThumb = "D:\\Appls\\Java\\pscp.exe -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk %s:/tmp/image-thumb.png D:\\Appls\\Java\\wso2as-5.2.1\\repository\\deployment\\server\\webapps\\infopoint\\media\\screenshots\\%s.png";

	protected final static String GetStatscmd = "d:\\appls\\Java\\plink.exe -ssh %s -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk \"/var/www/stats.sh > /tmp/stats.txt\"";
	protected final static String CopyStats = "D:\\Appls\\Java\\pscp.exe -l root -i D:\\Appls\\Java\\iprsakey\\Ip-private.ppk %s:/tmp/stats.txt D:\\Appls\\Java\\wso2as-5.2.1\\repository\\deployment\\server\\webapps\\infopoint\\media\\stats\\%s.txt";

	public Watchdog(){}
	
	public JSONArray getDevices() throws ClientProtocolException, IOException, ParseException{
		DefaultHttpClient client = new DefaultHttpClient();
    	client.getCredentialsProvider().setCredentials(AuthScope.ANY, new NTCredentials("AdmServ_InfoPoint", "Info2015Point", "", "DALMINE"));

    	// Retrieve "devices" list and populate mongo collection Devices
    	HttpGet request = new HttpGet("http://teamwork.tenaris.net/sites/comm_com_europe/Infopoint/_vti_bin/listdata.svc/" + 
    	"Devices?$select=Id,LastIP,Mac,FriendlyName,LastSignal");
    	request.addHeader("accept", "application/json");
    	HttpResponse response = client.execute(request);
    	StatusLine status =  response.getStatusLine();
    	if(status.getStatusCode()!=HttpStatus.SC_OK){
    		// log error
    		log.error("Http response " + status.getStatusCode() + ":" + status.getReasonPhrase() + " retrieving devices");
    		return null;
    	}

    	BufferedReader rd = new BufferedReader (new InputStreamReader(response.getEntity().getContent(), Charset.forName("UTF-8").newDecoder()));
    	JSONObject json = (JSONObject) new JSONParser().parse(rd);

    	JSONObject jsonResp = (JSONObject)json.get("d");
    	if(jsonResp==null){
    		log.error("Json returned " + rd + " retrieving devices");
    		// continue
    		return null;
    	}
    	return (JSONArray)jsonResp.get("results");

	}
	public void CheckNodes(){
		
		try {
			JSONArray devices = getDevices();
			long milisnow = new Date().getTime();
	    	for(int i = 0; i<devices.size(); i++){
	    		JSONObject device  = (JSONObject) devices.get(i);
	    		String lastIP = (String) device.get("LastIP");
	    		if(lastIP == null) continue;
    			String name = (String)device.get("FriendlyName");
    			if(name==null) name = (String) device.get("Mac");
    			if(name.toLowerCase().startsWith("screensaver")) continue;
    			
	    		String date = (String)device.get("LastSignal");
	    		if(date.length()<=8) continue;
    			date = date.substring(6, date.length()-2);
    			long milisdelta = milisnow - Long.parseLong(date) - /* 3 hours */ MilisAdjust;
    			// Date d = new Date(Long.parseLong(date));
    			long minutesdelta = milisdelta / 60000;

				//ping
				try{
					Socket soc = new Socket();
					soc.connect(new InetSocketAddress(lastIP, 22), 2000);
					soc.close();
				} catch (IOException oiex){
					log.warn("Device " + name + " (IP:" + lastIP + ") seems unreachable");
					continue;
				}
				
    			try{
        			String storekey = String.format(StoreKey, lastIP);
    				Process p = Runtime.getRuntime().exec(storekey);
    	            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");            
    	            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
    	            errorGobbler.start();
    	            outputGobbler.start();
    				if(p.waitFor(15, TimeUnit.SECONDS)){
    					log.info("Storehash ended with code " + p.exitValue());
    				} else {
    					log.info("Storehash Timed out");
    				}
    			} catch (Exception ex){
    				// if i can trap no connection -> update status
    				log.warn("Store hash", ex);
    			}
    			String cmd = "";
    			
    			// get stats and thumb
    			try{
    				cmd = String.format(GetStatscmd, lastIP);
    				Process p = Runtime.getRuntime().exec(cmd);
    	            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");            
    	            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
    	            errorGobbler.start();
    	            outputGobbler.start();
    				if(p.waitFor(15, TimeUnit.SECONDS)){
    					log.info("RET:"+ p.exitValue() + ". " + cmd);
    				} else {
    					log.info("TIMED OUT: " + cmd);
    				}
    			} catch (Exception ex){
    				// if i can trap no connection -> update status
    				log.warn("Stats: " + cmd, ex);
    			}    			
    			try{
    				cmd = String.format(CopyStats, lastIP, lastIP);
    				Process p = Runtime.getRuntime().exec(cmd);
    	            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");            
    	            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
    	            errorGobbler.start();
    	            outputGobbler.start();
    				if(p.waitFor(15, TimeUnit.SECONDS)){
    					log.info("RET:"+ p.exitValue() + ". " + cmd);
    				} else {
    					log.info("TIMED OUT: " + cmd);
    				}
    			} catch (Exception ex){
    				// if i can trap no connection -> update status
    				log.warn("Copy Stats: " + cmd, ex);
    			}
    			try{
    				cmd = String.format(GetThumbcmd, lastIP);
    				Process p = Runtime.getRuntime().exec(cmd);
    	            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");            
    	            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
    	            errorGobbler.start();
    	            outputGobbler.start();
    				if(p.waitFor(15, TimeUnit.SECONDS)){
    					log.info("RET:"+ p.exitValue() + ". " + cmd);
    				} else {
    					log.info("TIMED OUT: " + cmd);
    				}
    			} catch (Exception ex){
    				// if i can trap no connection -> update status
    				log.warn("Thumbnail " + cmd, ex);
    			}
    			try{
    				cmd = String.format(CopyThumb, lastIP, lastIP);
    				Process p = Runtime.getRuntime().exec(cmd);
    	            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");            
    	            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
    	            errorGobbler.start();
    	            outputGobbler.start();
    				if(p.waitFor(15, TimeUnit.SECONDS)){
    					log.info("RET:"+ p.exitValue() + ". " + cmd);
    				} else {
    					log.info("TIMED OUT: " + cmd);
    				}
    			} catch (Exception ex){
    				// if i can trap no connection -> update status
    				log.warn("Copy Thumb " + cmd, ex);
    			}    			
    			if(minutesdelta < 20 /*|| minutesdelta > (48 * 60)*/) continue;
    			try{
    				if(minutesdelta > 60) {
    					log.info("Rebooting device " + name + " (IP:" + lastIP + ")");
        				cmd = String.format(Rebootcmd, lastIP);    					
    				} else {
    					log.info("Refreshing device " + name + " (IP:" + lastIP + ")");
        				cmd = String.format(Refreshcmd, lastIP);
    				}
    				Process p = Runtime.getRuntime().exec(cmd);
    	            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");            
    	            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
    	            errorGobbler.start();
    	            outputGobbler.start();
    				if(p.waitFor(15, TimeUnit.SECONDS)){
    					log.info("RET:"+ p.exitValue() + ". " + cmd);
    				} else {
    					log.info("TIMED OUT: " + cmd);
    				}
    			} catch (Exception ex){
    				// if i can trap no connection -> update status
    				log.warn("Exec: " + cmd, ex);
    			}
	    	}
		} catch (Exception e) {
			log.error("Check nodes Error", e);
		} 	
	}
}