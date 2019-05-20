package ss;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.http.client.ClientProtocolException;
import org.json.simple.parser.ParseException;

import twitter4j.MediaEntity;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class TwitterRetriever extends ChannelRetriever {

	// user: @tenarisapp // newsaggregator@tenaris.com // tenaris014
	private static final String TWITTER_CONSUMER_KEY = "xvPcTqEVpCflkYToWQIctIsKc";
	private static final String TWITTER_SECRET_KEY = "AGmyqroMdfM22YcDco0w4ETgp8vrzICg91BqjhR1OGtoJfriro";
	private static final String TWITTER_ACCESS_TOKEN = "2849951645-y7MjGLZNa6TTgvnNPphdCeIbOrmFYZUC9bmCXBb";
	private static final String TWITTER_ACCESS_TOKEN_SECRET = "p7p39Vq2wB3zrEqNCeTzG92gT4iGUrsnr3yDMO7Fj6AuU";
	
	public TwitterRetriever(String channel, String channelId, String channelName){
		super(channel, channelId, channelName);
	   	channelTypeId = 3;
		channelType = "Twitter";
	}
	
    public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {

    	ConfigurationBuilder cb = new ConfigurationBuilder();
    	cb.setDebugEnabled(true)
    	    .setOAuthConsumerKey(TWITTER_CONSUMER_KEY)
    	    .setOAuthConsumerSecret(TWITTER_SECRET_KEY)
    	    .setOAuthAccessToken(TWITTER_ACCESS_TOKEN)
    	    .setOAuthAccessTokenSecret(TWITTER_ACCESS_TOKEN_SECRET);
    	cb.setTweetModeExtended(true);
    	TwitterFactory tf = new TwitterFactory(cb.build());
    	Twitter twitter = tf.getInstance();
    	
    	Paging p = new Paging();
    	p.setCount(40);
    	ResponseList<Status> posts = null;
    			
		try {
			posts = twitter.getUserTimeline(channelName, p);
		} catch (TwitterException e) {
			log.error(channelType + " " + channel + " Twitter error :",e);
			return;
		}
    	
    	initMongoDB();
    	
    	BasicDBObject filter = new BasicDBObject();
    	BasicDBObject fields = new BasicDBObject();
    	filter.put("channelType", channelTypeId);
    	filter.put("channelId", channelId);
    	fields.put("key", 1);
    	fields.put("v", 1);
    	fields.put("text", 1);
    	fields.put("image_local", 1); // should delete images too
    	DBCursor cur = postsCollection.find(filter, fields);
    	HashMap<String, DBObject> current = new HashMap<String, DBObject>();
    	
    	while(cur.hasNext()){
    		DBObject n = cur.next();
    		current.put(n.get("key").toString(), n);
    	}
    	
    	for(Status post: posts){
    		int ver = 1;
    		try{
	    		BasicDBObject oPost = new BasicDBObject();
	    		
	    		String objKey = channelTypeId + "/" + channelId + "/" + post.getId();
	    		oPost.put("text", replaceAnchors(post.getText()));
	    		oPost.put("id",post.getId());
	    		oPost.put("source",post.getSource());
	    		oPost.put("isRetweet",post.isRetweet());
	    		
	    		if(current.containsKey(objKey)){
	    			DBObject curr = current.remove(objKey);
	    			if(curr.get("text").toString().compareTo(oPost.get("text").toString())==0){
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
	    		long filesSize = 0;
	    		MediaEntity[] medias = post.getMediaEntities();
	    		if(medias.length>0){
	    			MediaEntity m = medias[0];
	    			String type =m.getType(); 
	    			oPost.put("key", type);
	    			if(type.compareTo("photo") == 0 || type.compareTo("animated_gif") == 0 ){
	    				filesSize += copyImage(ctx, objKey, oPost, "image", m.getMediaURL());
	    			} else if(type.compareTo("video") == 0){
	    				filesSize += copyImage(ctx, objKey, oPost, "image", m.getMediaURL());
	    				// TODO: come recuperare il video??
	    				// copyVideo(ctx, objKey, oPost, "video", m.getExpandedURL()); 
	    			} else {
	    				log.warn("Unhandled Media Type :" + type);
	    				continue;
	    			}
	    		}
	    		/*
	    		else if(urls.length > 0){
	    			// System.out.println(urls[0].getExpandedURL());
	    			log.info(channelType + " " + channel + " skipping posted url :" + urls[0].getExpandedURL());
	    			continue;
	    		}
	    		*/
	    		
	    		oPost.put("key", objKey);
	    		oPost.put("channelType", channelTypeId);
	    		oPost.put("channelName", channelName);
	    		oPost.put("channelId", channelId);
	    		oPost.put("date", outdf.format(post.getCreatedAt())); // some conversion to UTC here!!
	    		oPost.put("link", "https://twitter.com/" + post.getUser().getScreenName() + "/status/" + post.getId());
	    		oPost.put("v", ver);
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
    			gfsPhoto.remove(o.get("image_local").toString());
    		}		
    		postsCollection.remove(o);
    	}
    }

}
