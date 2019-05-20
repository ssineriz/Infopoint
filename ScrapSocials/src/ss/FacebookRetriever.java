package ss;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.client.ClientProtocolException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class FacebookRetriever extends ChannelRetriever {
	final String baseUrl = "https://graph.facebook.com/v2.9/";
	private String channelPath;

	
	public FacebookRetriever(String channel, String channelId, String channelName){
		super(channel, channelId, channelName);
	   	channelTypeId = 2;
		channelType = "Facebook"; 

    	String[] parts = channel.split("/");
    	this.channelPath = parts[parts.length -1];
    	initVideoTranscoder();
	}
	
    public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {
    	HashMap<String, String> headers = new HashMap<String, String>();
    	headers.put("Authorization", "Bearer " + Constants.FACEBOOK_OAUTH_TOKEN);
    	String fieldNames = "?fields=type,caption,created_time,event,description,picture,id,object_id,link,from,status_type,name,message,source,attachments,updated_time";
    	JSONObject json = getJson(baseUrl + "/" + channelPath + "/posts" + fieldNames, headers);
    	
    	JSONArray posts = (JSONArray)json.get("data");
    	  
    	initMongoDB();

    	List<String> typeNames = Arrays.asList(new String[]{"photo", "video", "status", "link", "event"});
    	
    	BasicDBObject filter = new BasicDBObject();
    	BasicDBObject fields = new BasicDBObject();
    	filter.put("channelType", channelTypeId);
    	filter.put("channelId", channelId);
    	fields.put("key", 1);
    	fields.put("updated_time", 1);
    	fields.put("v", 1);
    	fields.put("source_local", 1); // should delete images too
    	fields.put("picture_local", 1); // should delete images too
    	DBCursor cur = postsCollection.find(filter, fields);
    	HashMap<String, DBObject> current = new HashMap<String, DBObject>();
    	
    	while(cur.hasNext()){
    		DBObject n = cur.next();
    		current.put(n.get("key").toString(), n);
    	}
    	for(int i = 0; i<posts.size(); i++){
    		int ver = 1;
        	long filesSize = 0;
    		try{
    		JSONObject post = (JSONObject) posts.get(i);
    		String objKey = channelTypeId + "/" + channelId + "/" + post.get("id");

			if( (!post.containsKey("message") || post.get("message") == null) && 
				(!post.containsKey("description") || post.get("description") == null)) {
				if(current.containsKey(objKey))
					current.remove(objKey);
				continue;
			}
			
    		if(current.containsKey(objKey)){
    			DBObject curr = current.remove(objKey);
    			if(areKeysEqual(curr, post, "updated_time") ){
    				if(!ctx.isRefreshImages()) {
    					if( (post.get("source")==null || post.get("source_local")!=null)
    						&& (post.get("picture")==null || post.get("picture_local")!=null)) {
	    					log.info("Same version for " + objKey + ".F:" + curr.get("source_local") + ", M:" + curr.get("picture_local"));
	    					continue;
    					} else {
    						log.info("Missing images.");
    					}
    				}
    			} else {
    				Object oV = curr.get("v");
    				if(oV!=null){
    					ver = Integer.parseInt(oV.toString()) + 1;
    				}
    			}
    		}
    		
    		BasicDBObject oPost = null;
    		
    		new BasicDBObject();
    		switch(typeNames.indexOf(post.get("type"))){
	    		case 0:
	    			oPost = new BasicDBObject();	    			
	    			for(String k: new String[] {"id", "from", /*"message",*/ "type", "status_type", "object_id"}){
	    				oPost.put(k, post.get(k));
	    			}
	        		oPost.put("message",  replaceAnchors(post.get("message")));
	        		// retrieveImageId(ctx, objKey, oPost, post.get("object_id"));
	        		retrieveImageFromAttachment(ctx, objKey, oPost, post, "source");
	    			break;
	    		case 1:
	    			oPost = new BasicDBObject();
	    			for(String k: new String[] {"id", "description", /*"message",*/ "name", "status_type"}){
	    				oPost.put(k, post.get(k));
	    			}
	        		oPost.put("message",  replaceAnchors(post.get("message")));
	    			// copyImage(ctx, objKey, oPost, "picture", post.get("picture"));
	    			retrieveImageFromAttachment(ctx, objKey, oPost, post, "picture");
	    			try {
	    				filesSize += copyVideo(ctx, objKey, oPost, "source", post.get("source"));
	    			} catch (Exception ex){
	    				log.error(ex);
	    			}
	    			break;
	    		case 2:
	    			// NO IMAGES
	    			oPost = new BasicDBObject();
	    			for(String k: new String[] {"id", /*"message",*/ "status_type"}){
	    				oPost.put(k, post.get(k));
	    			}
	        		oPost.put("message",  replaceAnchors(post.get("message")));
	    			break;
	    		case 3:
	    		case 4:
	    			oPost = new BasicDBObject();
	    			for(String k: new String[] {"id", "description", /* "message",*/ "name", "status_type", "link", "caption"}){
	    				oPost.put(k, post.get(k));
	    			}
	        		oPost.put("message",  replaceAnchors(post.get("message")));
	    			// low res image with preview
	    			// copyImage(ctx, objKey, oPost, "picture", post.get("picture"));
	    			retrieveImageFromAttachment(ctx, objKey, oPost, post, "picture");
	    			break;
	    		default:
	    			log.warn(channel + " type " + post.get("type") + " unhandled in post :" + post.toJSONString());
    				// System.out.println(post.toJSONString());
    				continue;
    		}
    		
    		oPost.put("key", objKey);
    		oPost.put("type", post.get("type"));
    		oPost.put("channelType", channelTypeId);
    		oPost.put("channelName", channelName);
    		oPost.put("channelId", channelId);
    		oPost.put("v", ver);
    		oPost.put("updated_time", post.get("updated_time"));
    		oPost.put("date", post.get("created_time")); // some conversion to UTC here!!
    		oPost.put("link", post.get("link")); // Only exists for type:link. Should recreate link from post
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
    		} catch (Exception ex){
    			log.error("Error at channel " + channelType + "-" + channelName, ex);
    		}
    	}
    	
    	// delete remainders
    	for(Entry<String, DBObject> rest: current.entrySet()){
    		DBObject o = rest.getValue();
    		if(o.containsField("source_local")){
    			gfsPhoto.remove(o.get("source_local").toString());
    		}
    		if(o.containsField("picture_local")){
    			gfsPhoto.remove(o.get("picture_local").toString());
    		}    		
    		postsCollection.remove(o);
    	}
    }
/*
	private void retrieveImageId(ExecutionContext ctx, String objKey, BasicDBObject oPost, Object imgId) throws IOException,
			ClientProtocolException, ParseException {
		if(imgId!=null){
	    	HashMap<String, String> headers = new HashMap<String, String>();
	    	headers.put("Authorization", "Bearer " + token);
	    	JSONObject json = getJson(baseUrl + imgId.toString(), headers);
	    	oPost.put("source_alt", json.get("name"));
			copyImage(ctx, objKey, oPost, "source", json.get("source"));
		}
	}
*/
    private long retrieveImageFromAttachment(ExecutionContext ctx, String objKey, BasicDBObject dest, JSONObject src, String key) throws IOException,
	ClientProtocolException, ParseException {
    	long resp = 0;
    	if(src==null) return resp;
    	JSONObject attachments = (JSONObject)src.get("attachments");
		if(attachments == null) return resp;
		JSONArray attachmentList = (JSONArray)attachments.get("data");
		if(attachmentList == null || attachmentList.size() <1) return resp;
		JSONObject attachment = (JSONObject)attachmentList.get(0); //  ensure type=="photo" or "video_autoplay" or "share"
		// may also be "subattachments", in that case, I need the inner collection
		if(attachment.containsKey("subattachments")){
			attachments = (JSONObject)attachment.get("subattachments");
			if(attachments == null) return resp;
			attachmentList = (JSONArray)attachments.get("data");
			if(attachmentList == null || attachmentList.size() <1) return resp;
			attachment = (JSONObject)attachmentList.get(0);			
		}
		if(attachment.get("description") != null){
			dest.put("source_alt", attachment.get("description")); // or title (shorter), if exists
		} else if(attachment.get("title") != null){
			dest.put("source_alt", attachment.get("title"));
		}
		JSONObject media = (JSONObject)attachment.get("media");
		if(media==null) return resp;
		JSONObject image = (JSONObject)media.get("image");
		if(image==null) return resp;
		if(image.get("src")==null) return resp;
		resp = copyImage(ctx, objKey, dest, key, image.get("src").toString());
		return resp;
    }
}
