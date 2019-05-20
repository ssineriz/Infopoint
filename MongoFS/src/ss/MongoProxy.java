package ss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;


/**
 * Servlet implementation class MongoProxy
 */
public class MongoProxy extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Log log = LogFactory.getLog(MongoProxy.class);
	
	private ServletConfig config;
	private DB mongodb;
	private DBCollection collection;
	private DBCollection devices;
	private Mongo mongo;
    private DBObject fields;
	private DateFormat outdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
	
	private final String channel = "http://teamwork.tenaris.net/sites/comm_com_europe/Infopoint/_vti_bin/listdata.svc/";
	/**
     * @see HttpServlet#HttpServlet()
     */
    public MongoProxy() {
        super();
    }
    
	public void init(ServletConfig servletConfig) throws ServletException {
		this.config = servletConfig;
		this.fields = new BasicDBObject("_id", 1);
		this.fields.put("key", 1);
		this.fields.put("v", 1);
		this.fields.put("bytes", 1);		    
		try {			
			mongo = new Mongo(config.getInitParameter("mongoserver"), Integer.parseInt(config.getInitParameter("mongoport")));
			mongodb = mongo.getDB(config.getInitParameter("mongodb"));
			if(config.getInitParameter("mongouser")!=null){
				mongodb.authenticate(config.getInitParameter("mongouser"), config.getInitParameter("mongopass").toCharArray());
			}
			collection = mongodb.getCollection(config.getInitParameter("postscollection"));			
			devices = mongodb.getCollection(config.getInitParameter("devicescollection"));			
		} catch (NumberFormatException e) {
			log.error("Bad port number in config: " + config.getInitParameter("mongoport"), e);
		} catch (UnknownHostException e) {
			log.error("Mongo server not found " + config.getInitParameter("mongoserver") + ":" + config.getInitParameter("mongoport"), e);
		}
	}
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// 3 routes: 
		// - old
		// - new
		// - single post
		// - query all
		if(request.getParameter("key")!=null){
			getPostByKey(request.getParameter("key"), response);
			return;
		}
		if(request.getParameter("query")!=null){
			getPostsByQuery(request, response);
			return;
		}
		String mac = request.getParameter("dev");
		BasicDBObject filter = new BasicDBObject();
		DBObject dev = null;
		
		if(mac!=null){
			BasicDBObject findone = new BasicDBObject("mac", mac);
			devices.ensureIndex("mac");
			dev = devices.findOne(findone);
			if(dev==null){
				// should notify manager and trigger update
				signalNewDevice(mac, request);
				filter.append("channelType", new BasicDBObject("$ne",6));
			} else {
				String now = outdf.format(new Date());
				JSONObject devcon = pingDevice(mac, request, request.getParameter("probe")!=null);
				JSONObject channelroot = (JSONObject)devcon.get("Channels");
				JSONArray channels =  (JSONArray)channelroot.get("results");
				// For each -> channel("ChannelName"), channel("ChannelTypeId") -> or ( channelName and channelType )
				List<BasicDBObject> ors = new ArrayList<BasicDBObject>();
				for(int i = 0; i<channels.size(); i++){
					JSONObject channel = (JSONObject)channels.get(i);
					if(Integer.valueOf(channel.get("ChannelTypeId").toString())==6){
						List<BasicDBObject> args = Arrays.asList(
								new BasicDBObject("channelType", 6),
								new BasicDBObject("channelId", channel.get("Id").toString()), // ("channelName", channel.get("ChannelName"))
								new BasicDBObject("devices", mac),
								new BasicDBObject("$or", Arrays.asList( 
										new BasicDBObject("expiry", new BasicDBObject("$exists", 0)),
										new BasicDBObject("expiry", new BasicDBObject("$gte", now))
										))
							);
						BasicDBObject cond = new BasicDBObject("$and", args);
						ors.add(cond);
					} else {
						List<BasicDBObject> args = Arrays.asList(
								new BasicDBObject("channelType", channel.get("ChannelTypeId")),
								new BasicDBObject("channelId", channel.get("Id").toString())
							);
						BasicDBObject cond = new BasicDBObject("$and", args);
						ors.add(cond);
					}
				}
				filter.append("$or", ors);
			}
			BasicDBObject excludeKeys = new BasicDBObject("$nin", Arrays.asList("1/17/2998", "1/3/2997", "1/4/4652"/*,...*/));  // take from some config
			BasicDBObject filterExclude = new BasicDBObject("key", excludeKeys);
			filter = new BasicDBObject("$and", Arrays.asList(filterExclude, filter));
		}
		// Return last 100 posts sorted by date
		BasicDBObject orderBy = new BasicDBObject("KeepInFront", -1);
		orderBy.append("date", -1);

		DBCursor cursor;
		if (request.getParameter("dif")!=null){		
			cursor = collection.find(filter, fields).sort(orderBy).limit(100);
		} else {
			cursor = collection.find(filter).sort(orderBy).limit(100);
		}
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		PrintWriter out=response.getWriter();
		// Add specific device / location data (Plant.Config)
		out.print("{\"device\":");
		out.print(dev == null ? "null" : dev.toString());
		out.print(",\"serverTime\":\"");
		Date d = new Date();
		out.print(outdf.format(d));
		out.print("\",\"data\":[");
		boolean isFirst=true;
		try {
		    while(cursor.hasNext()) {
		    	DBObject el = cursor.next();
		    	if(isFirst){ isFirst=false; } else { out.print(",");}
		        out.print(el.toString());
		    }
		} finally {
		    cursor.close();
		}
		out.print("]}");
	}

	private void getPostByKey(String key, HttpServletResponse response) {
		try {
			collection.ensureIndex("key");
			DBObject post = collection.findOne(new BasicDBObject("key", key));
			if(post == null) {
				return;
			}
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out;
			out = response.getWriter();
			out.print(post.toString());
		} catch (Exception e) {
			e.printStackTrace();
			try{
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "{error:\"Key not found\"}");;
			} catch (Exception ex){
				ex.printStackTrace();
			}
		}
		return;
	}

	private void getPostsByQuery(HttpServletRequest request, HttpServletResponse response) {
		try {
			// may parse other query params for paging and channels
			BasicDBObject orderBy = new BasicDBObject("date", -1);
			DBCursor cursor;
			//cursor = collection.find(filter, fields).sort(orderBy).limit(100);

			cursor = collection.find().sort(orderBy).limit(200);

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.addHeader("Access-Control-Allow-Origin", "*");
			PrintWriter out=response.getWriter();

			out.print("{\"data\":[");
			boolean isFirst=true;
			try {
			    while(cursor.hasNext()) {
			    	DBObject el = cursor.next();
			    	if(isFirst){ isFirst=false; } else { out.print(",");}
			        out.print(el.toString());
			    }
			} finally {
			    cursor.close();
			}
			out.print("]}");

		} catch (Exception e) {
			e.printStackTrace();
			try{
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "{error:\"Key not found\"}");;
			} catch (Exception ex){
				ex.printStackTrace();
			}
		}
		return;
	}	
	
	private void signalNewDevice(String mac, HttpServletRequest request) {
		String ip = request.getRemoteAddr();
		// total, free, used
		DefaultHttpClient client = new DefaultHttpClient();
    	client.getCredentialsProvider().setCredentials(AuthScope.ANY, new NTCredentials("AdmServ_InfoPoint", "Info2015Point", "", "DALMINE"));
    	HttpPost post = new HttpPost(channel + "Devices");
    	post.addHeader("accept", "application/json");
    	post.addHeader("Content-Type", "application/json");
    	post.setEntity(new StringEntity("{\"Mac\":\"" + mac + "\", \"Description\":\"Added @ " + new Date().toString() + 
    			"\", \"LastSignal\":\"" + outdf.format(new Date()) + "\", \"LastIP\":\"" + ip + 
    			"\", \"Total\":" + request.getParameter("total") + 
    			", \"Free\":" + request.getParameter("free") + 
    			", \"Used\":" + request.getParameter("used") + 
    			"}", ContentType.APPLICATION_JSON));
    	HttpResponse response;
		try {
			response = client.execute(post);
	    	StatusLine status =  response.getStatusLine();
	    	if(status.getStatusCode()!=HttpStatus.SC_CREATED){
	    		log.error("Bad response from Sharepoint site while creating a new device:" + status.getStatusCode() + ":" + status.getReasonPhrase());
	    		return;
	    	}
		} catch (ClientProtocolException e) {
			log.error(e);
			return;
		} catch (IOException e) {
			log.error(e);
			return;
		}

		// if ok, create element in collection
		BasicDBObject dev = new BasicDBObject("mac", mac);
		devices.findAndModify(
				new BasicDBObject("mac", mac), 				// query
				null, 										// fields
				null,										// sort
				false,										// remove
				dev,										// new object
				true,										// return new
				true										// create if  empty
				);
		
	}

	private JSONObject pingDevice(String mac, HttpServletRequest request, boolean probe) {
		// send notification to Sharepoint
		String ip = request.getRemoteAddr();
		DefaultHttpClient client = new DefaultHttpClient();
    	HttpResponse response;
    	client.getCredentialsProvider().setCredentials(AuthScope.ANY, new NTCredentials("AdmServ_InfoPoint", "Info2015Point", "", "DALMINE"));
    	
    	// http://teamwork.tenaris.net/sites/comm_com_europe/Infopoint/_vti_bin/listdata.svc/Devices?$filter=Mac eq 'b8:27:eb:5c:51:71'&$select=Id
    	String sq=""; 
    	try {
			sq = URLEncoder.encode("Mac eq '" + mac + "'", "UTF-8");
		} catch (UnsupportedEncodingException e1) {
    		log.error(e1);
    		return null;
		}
    	HttpGet get = new HttpGet(channel + "Devices?$filter=" + sq + "&$select=Id,LastIP,FriendlyName,Channels,PageFlipSeconds,IdleTimeoutSeconds&$expand=Channels");
    	get.addHeader("accept", "application/json");
    	
    	JSONObject dev = null;
		try {
			response = client.execute(get);
	    	StatusLine status =  response.getStatusLine();
	    	if(status.getStatusCode()!=HttpStatus.SC_OK){
	    		log.error("Bad response from Sharepoint site while pinging device:" + status.getStatusCode() + ":" + status.getReasonPhrase());
	    		return null;
	    	}
	    	
	    	BufferedReader rd = new BufferedReader (new InputStreamReader(response.getEntity().getContent(), Charset.forName("UTF-8").newDecoder()));
	    	JSONObject json = (JSONObject) new JSONParser().parse(rd);

	    	JSONObject jsonResp = (JSONObject)json.get("d");
	    	if(jsonResp==null){
	    		log.error("Json returned " + rd + " retrieving device for proxy");
	    		// continue
	    		return null;
	    	}
	    	JSONArray devices = (JSONArray)jsonResp.get("results");
	    	dev=(JSONObject)devices.get(0);
	    	
		} catch (ClientProtocolException e) {
			log.error(e);
			return null;
		} catch (IOException e) {
			log.error(e);
			return null;
		} catch (ParseException e) {
			log.error(e);
			return null;
		}
    	if(dev==null){
    		log.warn("Failed to retrieve object with mac " + mac);
    		return null;
    	}
    	if(probe) {
    		// don't update last signal
    		return dev;
    	}
    	JSONObject meta = (JSONObject)dev.get("__metadata");
    	
    	HttpPost post = new HttpPost(meta.get("uri").toString());
    	post.addHeader("accept", "application/json");
    	post.addHeader("Content-Type", "application/json");
    	post.addHeader("If-Match", meta.get("etag").toString());
    	post.addHeader("X-HTTP-Method", "MERGE");
    	
    	post.setEntity(new StringEntity("{\"LastSignal\":\"" + outdf.format(new Date()) + "\", \"LastIP\":\"" + ip +
    			"\", \"Total\":" + request.getParameter("total") + 
    			", \"Free\":" + request.getParameter("free") + 
    			", \"Used\":" + request.getParameter("used") + 			
    			"}", ContentType.APPLICATION_JSON));

		try {
			response = client.execute(post);
	    	StatusLine status =  response.getStatusLine();
	    	if(status.getStatusCode()!=HttpStatus.SC_NO_CONTENT){
	    		String devname = (String )dev.get("FriendlyName");
	    		if(devname==null) devname="";
	    		devname = (devname==null? "(" : devname + " (") + dev.get("LastIP") + ")"; 
	    		log.error("Bad response from Sharepoint site updating last signal for device " + devname + ":" + status.getStatusCode() + ":" + status.getReasonPhrase());
	    	}
		} catch (ClientProtocolException e) {
			log.error(e);
		} catch (IOException e) {
			log.error(e);
		}
		return dev;
	}
}
