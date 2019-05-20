package ss;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.http.client.ClientProtocolException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class InstagramRetriever extends ChannelRetriever {

	final String baseUrl = "https://api.instagram.com/v1/";

	public InstagramRetriever(String channel, String channelId, String channelName){
		super(channel, channelId, channelName);
	   	channelTypeId = 4;
		channelType = "Instagram";
	}
	
    public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {

    	String [] split = channel.split("/");
    	String username = split[split.length -1];    	
    	JSONObject json = getJson(baseUrl + "users/search?q=" + username + "&client_id=" + Constants.INSTAGRAM_CLIENT_ID);
 
    	JSONArray users = (JSONArray)json.get("data");
    	String userid=null; //"253718685";
    	for(int i = 0; i<users.size(); i++){
    		JSONObject user = (JSONObject)users.get(i);
    		if(user.get("username").toString().equalsIgnoreCase(username)){
    			userid = user.get("id").toString();
    			break;
    		}
    	}
    	if(userid == null){
    		log.error("No Instagram user " + username + " found for channel " + channelType + "-" + channelName);
    		return;
    	}
     	// users/{user-id}/media/recent/?client_id=YOUR-CLIENT_ID
    	json = getJson(baseUrl + "users/" + userid + "/media/recent/?count=30&client_id=" + Constants.INSTAGRAM_CLIENT_ID);

    	JSONArray posts = (JSONArray)json.get("data");
    	
    	initMongoDB();
    	
    	BasicDBObject filter = new BasicDBObject();
    	BasicDBObject fields = new BasicDBObject();
    	filter.put("channelType", channelTypeId);
    	filter.put("channelId", channelId);
    	fields.put("key", 1);
    	fields.put("image_local", 1);
    	DBCursor cur = postsCollection.find(filter, fields);
    	HashMap<String, DBObject> current = new HashMap<String, DBObject>();
    	
    	while(cur.hasNext()){
    		DBObject n = cur.next();
    		current.put(n.get("key").toString(), n);
    	}
	
    	for(int i = 0; i<posts.size(); i++){
    		try{
	    		JSONObject post = (JSONObject) posts.get(i);
	    		if(post.get("type").toString().compareToIgnoreCase("image")!=0) continue;
	    		
	    		String objKey = channelTypeId + "/" + channelId + "/" + post.get("id");
	    		
	    		BasicDBObject oPost = new BasicDBObject();
	    		
	    		oPost.put("key", objKey);
	    		oPost.put("channelType", channelTypeId);
	    		oPost.put("channelName", channelName);
	    		oPost.put("channelId", channelId);
	    		Date created = new Date(Long.parseLong(post.get("created_time").toString())*1000);
	    		///*datetime*1000 = milis*/post.get("created_time");
	    		long filesSize = 0;
	    		oPost.put("date",outdf.format(created)); // some conversion to UTC here!!
	    		
				oPost.put("id", post.get("id"));
				oPost.put("type", post.get("type"));
				oPost.put("link", post.get("link"));
				JSONObject captionObject = (JSONObject)post.get("caption");
				if(captionObject!=null){
					oPost.put("caption", replaceAnchors(captionObject.get("text")));
				}
				JSONObject imgObj = (JSONObject)post.get("images");
				if(imgObj!=null){
					JSONObject srObj = (JSONObject)imgObj.get("standard_resolution");
					if(srObj!=null) {
						filesSize += copyImage(ctx, objKey, oPost, "image", srObj.get("url"));
					}
				}
				
	    		current.remove(objKey);
	    		oPost.put("bytes", filesSize + oPost.toString().length());
	    		oPost.put("v", 1); // no versioning possible
	    		
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
    	
		for(Entry<String, DBObject> rest: current.entrySet()){
    		DBObject o = rest.getValue();
    		if(o.containsField("source_local")){
    			gfsPhoto.remove(o.get("image_local").toString());
    		}		
    		postsCollection.remove(o);
    	}
    	
    	 
    }
    
}
