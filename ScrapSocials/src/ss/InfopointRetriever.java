package ss;

import it.sauronsoftware.jave.Encoder;
import it.sauronsoftware.jave.EncoderException;
import it.sauronsoftware.jave.InputFormatException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

public class InfopointRetriever extends ChannelRetriever {
	
	public InfopointRetriever(JSONObject channelObj){
		super(channelObj);
    	initVideoTranscoder();
	}
	
    public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {

    	// Specific
    	client.getCredentialsProvider().setCredentials(AuthScope.ANY, new NTCredentials("AdmServ_InfoPoint", "Info2015Point", "", "DALMINE"));

    	// Retrieve "devices" list and populate mongo collection Devices
    	HashMap<String, String> headers = new HashMap<String, String>();
    	headers.put("accept", "application/json");
    	JSONObject json = getJson(channel + "Devices?$select=Id,Mac,Plant,Description,FriendlyName,PageFlipSeconds,IdleTimeoutSeconds&$expand=Plant", headers);

    	JSONObject jsonResp = (JSONObject)json.get("d");
    	if(jsonResp==null){
    		log.error("Json returned empty retrieving device conf for channel " + channelType + "-" + channelName);
    		// continue
    		return;
    	}
    	JSONArray devices = (JSONArray)jsonResp.get("results");
    	
    	initMongoDB();
    	DBCollection devcoll = mongoDb.getCollection("devices");
    	

    	devcoll.ensureIndex("mac");
    	HashMap<String, DBObject> currMacs = new HashMap<String, DBObject>();
    	/*
    	DBObject ob;
    	while( (ob = devcoll.findOne()) != null){  
    		// devcoll.remove(ob); // could drop and recreate but requires higher permissions...
    		currMacs.put(ob.get("mac").toString(), ob);
    	}
    	*/
    	DBCursor devcur = devcoll.find();
    	while(devcur.hasNext()){
    		DBObject ob = devcur.next();
    		currMacs.put(ob.get("mac").toString(), ob);
    	}    	
    	
    	for(int i =0; i<devices.size(); i++){
    		try{
	    		JSONObject dev = (JSONObject) devices.get(i);
	    		BasicDBObject oPost = new BasicDBObject();
	    		
	    		oPost.put("mac", dev.get("Mac"));
	    		oPost.put("friendlyName", dev.get("FriendlyName"));
	    		oPost.put("description", dev.get("Description"));
	    		oPost.put("pageFlipSeconds", dev.get("PageFlipSeconds"));
	    		oPost.put("idleTimeoutSeconds", dev.get("IdleTimeoutSeconds"));
	    		JSONObject plnt = (JSONObject) dev.get("Plant");
	    		if(plnt!=null){
	    			oPost.put("plant", plnt.get("Plant"));
	    			// oPost.put("config", plnt.get("Config"));
	    			oPost.put("language", plnt.get("LanguageValue"));
	    		}
	    		
	    		devcoll.findAndModify(
	    				new BasicDBObject("mac", dev.get("Mac")), 	// query
	    				null, 										// fields
	    				null,										// sort
	    				false,										// remove
	    				oPost,										// new object
	    				true,										// return new
	    				true										// create if  empty
	    				);
	    		currMacs.remove(dev.get("Mac"));
			} catch (Exception ex){
				log.error("Error at channel " + channelType + "-" + channelName, ex);
			}
    	}
    	// delete remainders
    	for(Entry<String, DBObject> restMac: currMacs.entrySet()){
    		DBObject rDev = restMac.getValue();
    		devcoll.remove(rDev);
    	}    	
    	

    	json = getJson(channel + "Posts?$select=Id,Title,Date,Excerpt,Text,Attachments,Category,Device,ImageUrl,VideoUrl,Expiry,Columns,Color,HideLabel,KeepInFront,Modified&$expand=Attachments,Device", headers); // ,Category

    	jsonResp = (JSONObject)json.get("d");
    	if(jsonResp==null){
    		log.error("Json returned empty for channel " + channelType + "-" + channelName);
    		// continue
    		return;
    	}
    	JSONArray posts = (JSONArray)jsonResp.get("results");
    	
    	BasicDBObject filter = new BasicDBObject();
    	BasicDBObject fields = new BasicDBObject();
    	filter.put("channelType", channelTypeId);
    	filter.put("channelId", channelId);
    	fields.put("key", 1);
    	fields.put("Modified", 1);
    	fields.put("v", 1);
    	fields.put("Attachments_local", 1); // should delete images too
    	DBCursor cur = postsCollection.find(filter, fields);
    	HashMap<String, DBObject> current = new HashMap<String, DBObject>();
    	
    	while(cur.hasNext()){
    		DBObject n = cur.next();
    		current.put(n.get("key").toString(), n);
    	}
    	
    	for(int i = 0; i<posts.size(); i++){
    		// find by id in collection
    		// replace/add by Id (key-> typeId, name, specificId)
    		JSONObject post = (JSONObject) posts.get(i);
    		String objKey = channelTypeId + "/" + channelId + "/" + post.get("Id");
    		int ver = 1;
    		// Skip same object
    		if(current.containsKey(objKey)){
    			DBObject curr = current.remove(objKey);
    			if(areKeysEqual(curr, post, "Modified")){
    				if(!ctx.isRefreshImages()) {
    					continue;
    				}
    			} else {
    				Object oV = curr.get("v");
    				if(oV!=null){
    					ver = Integer.parseInt(oV.toString()) + 1;
    				}
    			}
    		}
    		
    		BasicDBObject oPost = new BasicDBObject();
    		oPost.putAll(post);
    		oPost.put("Text",  replaceAnchors(post.get("Text")));
    		oPost.put("key", objKey);
    		oPost.put("v", ver);
    		// parse devices
    		JSONObject device = (JSONObject)post.get("Device");
    		if(device!=null){
    			devices = (JSONArray)device.get("results");
    			if(!devices.isEmpty()){
    				List<String> devlist = new ArrayList<String>();
    				for(int j=0; j<devices.size(); j++){
    					JSONObject devData = (JSONObject)devices.get(j);
    					String devName = devData.get("Mac").toString();
    					devlist.add(devName);
    				}
    				oPost.put("devices", devlist);
    			}
    			oPost.remove("Device");
    		}
    		
    		oPost.put("channelType", channelTypeId);
       		oPost.put("channelId", channelId);
       		oPost.put("channelName", channelName);
    		
    		String date = post.get("Date").toString();
			if(date.length()>8){
				date = date.substring(6, date.length()-2);
				Date d = new Date(Long.parseLong(date));
				oPost.put("date", outdf.format(d));
			}
			Object oExpiry = post.get("Expiry");
    		
			if(oExpiry !=null && oExpiry.toString().length()>8){
				String expiry = oExpiry.toString();
				expiry = expiry.substring(6, expiry.length()-2);
				Date d = new Date(Long.parseLong(expiry));
				oPost.put("expiry", outdf.format(d));
			}
			Object oCols = post.get("Columns");
			if(oCols !=null){
				oPost.put("columns", Integer.valueOf(((Long)oCols).intValue()));
			} else {
				oPost.put("columns", 1);
			}
			Object oColor = post.get("Color");
			if(oColor!=null){
				oPost.put("color", oColor.toString());
			} else {
				oPost.put("color", "cd25af");
			}
			
    		long filesSize = copyImage(ctx, client, gfsPhoto, post, objKey, oPost, "Attachments");
    		oPost.put("bytes", filesSize + oPost.toString().length());
    		postsCollection.ensureIndex("key");
			// log.warn("Updating with " + oPost.toString());

    		postsCollection.findAndModify(
    				new BasicDBObject().append("key", objKey), 	// query
    				null, 										// fields
    				null,										// sort
    				false,										// remove
    				oPost,										// new object
    				true,										// return new
    				true										// create if  empty
    				);

    	}
    	// delete remainders
    	for(Entry<String, DBObject> rest: current.entrySet()){
    		DBObject o = rest.getValue();
			// log.warn("Removing old " + o.get("key"));

    		if(o.containsField("Attachments_local")){
    			gfsPhoto.remove(o.get("Attachments_local").toString());
    		}
    		postsCollection.remove(o);
    	} 	  
    }

	private long copyImage(ExecutionContext ctx, HttpClient client, GridFS gfsPhoto, JSONObject post,
			String objKey, BasicDBObject oPost, String key) throws IOException,
			ClientProtocolException {
		long resp = 0;
		JSONObject attachments = (JSONObject)post.get(key);
		if(attachments==null){
			return resp;
		}
		JSONArray atts = (JSONArray)attachments.get("results");
		if(atts.isEmpty()) {
			return resp;
		}
		JSONObject attachment = (JSONObject)atts.get(0);
		String fullPath = ((JSONObject)attachment.get("__metadata")).get("media_src").toString();
	
		String fileName = attachment.get("Name").toString().replaceAll(" ", "%20");

		String imgKey = objKey + "/" + key + "/" + fileName;
		List<GridFSDBFile> olds = gfsPhoto.find(imgKey);
		if(olds.isEmpty() || ctx.isRefreshImages()){
			HttpGet req=null;
			try {
				// may have issues with whitespaces in filename
				req = new HttpGet(new URIBuilder(fullPath.replaceAll(" ", "%20")).build());
			} catch (URISyntaxException e) {
				log.error(e);
				oPost.removeField(key + "_local");
				return resp;
			}
			HttpResponse fresp = client.execute(req);
			StatusLine status = fresp.getStatusLine();
			if(status.getStatusCode()!=HttpStatus.SC_OK){
				// failed loading image
	    		log.error("Http response " + status.getStatusCode() + ":" + status.getReasonPhrase() + " for image " + fullPath + " in channel " + channelType + "-" + channelName);
	    		oPost.removeField(key + "_local");
	    		req.releaseConnection();
			} else {
				GridFSInputFile gfsFile = gfsPhoto.createFile(fresp.getEntity().getContent(), imgKey, true);
				resp = gfsFile.getLength();
				gfsFile.save();
				oPost.put(key + "_local", imgKey);
			}
		} else {
			resp = olds.get(0).getLength();
			oPost.put(key + "_local", imgKey);
		}
		if(atts.size()>1){
			// video
			JSONObject vattachment = (JSONObject)atts.get(1);
			String vidPath = ((JSONObject)vattachment.get("__metadata")).get("media_src").toString();
		
			String vkey = "source";

			
			//String vidKey = objKey + "/video.mp4"; // fixed
			
			String vidKey = objKey.replace("\\", "_").replace("/", "_") + "_v.mp4"; 
			// replace path chars in filename
			// check file exists
			// 
			String[] parts = vidPath.split("/");
			String vidFileName = parts[parts.length-1].split("\\?")[0];
			String vidExt = ".mov";
			int eix = vidFileName.lastIndexOf('.');
			if (eix > 0) {
				vidExt = vidFileName.substring(eix);
			}
			// List<GridFSDBFile> olds = gfsPhoto.find(vidKey);
			File vidFile = new File(mediaPath + vidKey);
			
			if(!vidFile.exists() || ctx.isRefreshImages()){
				HttpGet req=null;
				try {
					// may have issues with whitespace in filename
					req = new HttpGet(new URIBuilder(vidPath.replaceAll(" ", "%20")).build());
				} catch (URISyntaxException e) {
					log.error(e);
					oPost.removeField(vkey + "_local");
					return resp;
				}
				HttpResponse fresp = client.execute(req);
				if(fresp.getStatusLine().getStatusCode()!=HttpStatus.SC_OK){
					// failed loading image
		    		log.error("Http response " + fresp.getStatusLine().getStatusCode() + ":" + fresp.getStatusLine().getReasonPhrase() + " for video " + vidPath + " in channel " + channelType + "-" + channelName);
		    		oPost.removeField(vkey + "_local");
		    		req.releaseConnection();
				} else {
					InputStream initialStream = fresp.getEntity().getContent();
					File temp = File.createTempFile("video", vidExt); 
				    OutputStream tempStream = new FileOutputStream(temp);
				 
				    byte[] buffer = new byte[8 * 1024];
				    int bytesRead;
				    while ((bytesRead = initialStream.read(buffer)) != -1) {
				    	tempStream.write(buffer, 0, bytesRead);
				    }
				    tempStream.close();
				    // File resized = File.createTempFile("video", "mp4"); 
					Encoder encoder = new Encoder();

					try {
						encoder.encode(temp, vidFile, attrs);
						/*
					    FileInputStream resizedStream = new FileInputStream(resized);
						GridFSInputFile gfsFile = gfsPhoto.createFile(resizedStream, vidKey, true);
						gfsFile.save();
						resizedStream.close();
						*/
					    temp.deleteOnExit();
						oPost.put(vkey + "_local", vidKey);
						resp += vidFile.length();
					} catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InputFormatException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (EncoderException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				    // resized.deleteOnExit();

				}
			} else {
				oPost.put(vkey + "_local", vidKey);
				resp += vidFile.length();
			}
		}
		return resp;
	}
}
