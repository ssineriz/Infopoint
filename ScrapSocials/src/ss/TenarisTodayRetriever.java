package ss;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.http.client.ClientProtocolException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class TenarisTodayRetriever extends ChannelRetriever {

	private static final String iframeToken = "http://tenaristv.tenaris.net/tenaristv/www/iFrame.aspx?id=";
	
	public TenarisTodayRetriever(String channel, String channelId, String channelName) {
		super(channel, channelId, channelName);
	   	channelTypeId = 1;
		channelType = "TenarisToday";
    	initVideoTranscoder();
	}
	
    public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {
    	JSONObject json = null;
    	try {
    		json = getJson(channel);
    	} catch (Exception ex){
    		log.error("Error retrieving channel " + channelType + "-" + channelName + ": " + ex.getMessage());
    		return;
    	}

    	JSONObject jsonStatus = (JSONObject)json.get("Estado");
    	if(jsonStatus==null || (Long)jsonStatus.get("Codigo")!=0){
    		log.error("Json returned " + jsonStatus.get("Codigo") + ":" + jsonStatus.get("Mensaje") + " for channel " + channelType + "-" + channelName);
    		// continue
    		return;
    	}
    	JSONArray posts = (JSONArray)json.get("Noticias");
    	  
    	initMongoDB();

    	DateFormat indf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    	
    	BasicDBObject filter = new BasicDBObject();
    	BasicDBObject fields = new BasicDBObject();
    	filter.put("channelType", channelTypeId);
    	filter.put("channelId", channelId);
    	fields.put("key", 1);
    	fields.put("Titulo", 1);
    	fields.put("Subtitulo", 1);
    	fields.put("v", 1);
    	fields.put("Foto_local", 1); // should delete images too
    	fields.put("Miniatura_local", 1); // should delete images too
    	DBCursor cur = postsCollection.find(filter, fields);
    	HashMap<String, DBObject> current = new HashMap<String, DBObject>();
    	
    	while(cur.hasNext()){
    		DBObject n = cur.next();
    		current.put(n.get("key").toString(), n);
    	}
    	log.info("Retrieved posts: " + posts.size());
    	for(int i = 0; i<posts.size(); i++){
       		int ver = 1;
    		try{
    		// find by id in collection
    		// replace/add by Id (key-> typeId, name, specificId)
    		JSONObject post = (JSONObject) posts.get(i);
    		String objKey = channelTypeId + "/" + channelId + "/" + post.get("Id");

    		if(current.containsKey(objKey)){
    			DBObject curr = current.remove(objKey);
    			// No better versioning criteria
    			if(areKeysEqual(curr, post, "Titulo")
    				&& areKeysEqual(curr, post, "Subtitulo")){
    				if(!ctx.isRefreshImages()) {
    					if( (post.get("Foto")==null || post.get("Foto_local")!=null)
    						&& (post.get("Miniatura")==null || post.get("Miniatura_local")!=null)) {
	    					log.info("Same version for " + objKey + ".F:" + curr.get("Foto_local") + ", M:" + curr.get("Miniatura_local"));
	    					continue;
    					} else {
    						log.info("Missing images.");
    					}
    				}
    				log.info("Refreshing image for: " + objKey);
    			} else {
    				Object oV = curr.get("v");
    				if(oV!=null){
    					ver = Integer.parseInt(oV.toString()) + 1;
    				}
    				log.info("New version: " + ver);
    			}
    		} else {
				log.info("Key not found (new item) " + objKey);    			
    		}
    		
    		BasicDBObject oPost = new BasicDBObject();
    		oPost.putAll(post);
    		oPost.put("key", objKey);
    		oPost.put("channelType", channelTypeId);
    		oPost.put("channelId", channelId);
    		oPost.put("channelName", channelName);
    		oPost.put("v", ver);
    		
    		try {
    			// some conversion to UTC here!!
    			oPost.put("date", outdf.format(indf.parseObject(post.get("Fecha").toString())));
			} catch (java.text.ParseException e) {
				oPost.put("date", post.get("Fecha"));
			} 
    		// id:
    		// Date: Fecha
    		// Limit text length:
    		String text = post.get("Texto").toString(); 
    		if(text.length() > 500000){
    			log.warn("Content too long " + objKey);
    			// continue;
    		}
    		// images: Foto, Miniatura (replace url)
    		long filesSize = copyImage(ctx, objKey, oPost, "Foto", post.get("Foto"));
    		filesSize += copyImage(ctx, objKey, oPost, "Miniatura", post.get("Miniatura"));

    		// video?
    		//"http://tenaristv.tenaris.net/tenaristv/www/iFrame.aspx?id=..."
    		
    		int ifix = text.indexOf(iframeToken);
    		if(ifix>0){
    			String videoBaseUrl = text.substring(ifix + iframeToken.length(), text.indexOf("\"", ifix));
    			// sometimes it contains empty spaces...
    			videoBaseUrl = videoBaseUrl.replaceAll(" ", "");
    			filesSize += copyVideo(ctx, objKey, oPost, "source", videoBaseUrl + "/video.flv");
    			String cleantext = text.replaceAll("iframe", "comment");
    			oPost.put("Texto", replaceAnchors(cleantext));
    		} else {
        		oPost.put("Texto",  replaceAnchors(text));
    		}
    		oPost.put("bytes", filesSize + oPost.toString().length());
    		postsCollection.ensureIndex("key");
    		
    		postsCollection.findAndModify(
    				new BasicDBObject().append("key", objKey), 	// query
    				null, 										// fields
    				null,										// sort
    				false,										// remove
    				oPost,										// new object
    				true,										// return new
    				true										// create if  empty
    				);
    		

		  //System.out.println(post.get("type") + ":" + post.get("message"));
    		} catch (Exception ex){
    			log.error("Error at channel " + channelType + "-" + channelName, ex);
    		}
    	}
    	for(Entry<String, DBObject> rest: current.entrySet()){
    		DBObject o = rest.getValue();
    		if(o.containsField("Foto_local")){
    			gfsPhoto.remove(o.get("Foto_local").toString());
    		}
    		if(o.containsField("Miniatura_local")){
    			gfsPhoto.remove(o.get("Miniatura_local").toString());
    		}
    		log.info("Removing " + o.get("key"));
    		postsCollection.remove(o);
    	}
    }
	
}
