package ss;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.client.ClientProtocolException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class TenarisTodayRssRetriever extends ChannelRetriever {
	
	public TenarisTodayRssRetriever(JSONObject channelObj) {
		super(channelObj);
    	initVideoTranscoder();
	}
	
    public void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException {
    	Document doc = null;
    	try {
    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    		DocumentBuilder db = dbf.newDocumentBuilder();
    		Reader reader = new InputStreamReader(new URL(channel).openStream(), "utf-8");
    		InputSource source = new InputSource(reader);
    		doc = db.parse(source);
    	} catch (Exception ex){
    		log.error("Error retrieving channel " + channelType + "-" + channelName + ": " + ex.getMessage());
    		return;
    	}
    	Element rss = doc.getDocumentElement();
    	Element chans = (Element) rss.getElementsByTagName("channel").item(0);

    	NodeList posts = chans.getElementsByTagName("item");
    	  
    	initMongoDB();

    	DateFormat indf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
    	
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
    	log.info("Retrieved posts: " + posts.getLength());
    	for(int i = 0; i<posts.getLength(); i++){
       		int ver = 1;
    		try{
    		// find by id in collection
    		// replace/add by Id (key-> typeId, name, specificId)
    		Element post = (Element) posts.item(i);
    		String guid = post.getElementsByTagName("guid").item(0).getTextContent().trim();
    		// strip id only (hope is good enough)
    		String postId = guid.substring(guid.lastIndexOf("/") + 1);
    		String objKey = channelTypeId + "/" + channelId + "/" + postId;

    		Element enclosure = (Element) post.getElementsByTagName("enclosure").item(0);
    		if(current.containsKey(objKey)){
    			DBObject curr = current.remove(objKey);
    			// No better versioning criteria
   			
    			if(areSameValues(curr, "Titulo", post, "title")
    				&& areSameValues(curr, "Subtitulo", post, "description")){
    				if(!ctx.isRefreshImages()) {
    					if(enclosure == null && curr.get("Foto_local") == null
    							|| enclosure != null && enclosure.getAttribute("url") == curr.get("Foto_local")) {
	    					log.info("Same version for " + objKey + ".F:" + curr.get("Foto_local"));
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
    		oPost.put("Titulo", post.getElementsByTagName("title").item(0).getTextContent());
    		oPost.put("Subtitulo", post.getElementsByTagName("description").item(0).getTextContent());
    		oPost.put("Categoria", post.getElementsByTagName("category").item(0).getTextContent());
    		oPost.put("link", post.getElementsByTagName("link").item(0).getTextContent());
    		oPost.put("key", objKey);
    		oPost.put("channelType", channelTypeId);
    		oPost.put("channelId", channelId);
    		oPost.put("channelName", channelName);
    		oPost.put("v", ver);
    		
    		String pubDate = post.getElementsByTagName("pubDate").item(0).getTextContent();
    		pubDate = pubDate.replace("Z", "+0000");
    		try {
    			oPost.put("date", outdf.format(indf.parseObject(pubDate)));
			} catch (java.text.ParseException e) {
				oPost.put("date", pubDate);
			} 

    		/*
    		String text = post.get("Texto").toString(); 
    		if(text.length() > 500000){
    			log.warn("Content too long " + objKey);
    			// continue;
    		}
    		*/

    		long filesSize = 0;
    		if(enclosure!=null) {
    			filesSize = copyImage(ctx, objKey, oPost, "Foto", enclosure.getAttribute("url"));
    		}
    		
        	// oPost.put("Texto",  replaceAnchors(text));
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
    	for(Entry<String, DBObject> rest: current.entrySet()){
    		DBObject o = rest.getValue();
    		if(o.containsField("Foto_local")){
    			gfsPhoto.remove(o.get("Foto_local").toString());
    		}
    		log.info("Removing " + o.get("key"));
    		postsCollection.remove(o);
    	}
    }
    
	private Boolean areSameValues(DBObject a, String akey, org.w3c.dom.Element b, String bkey){
		if(a.containsField(akey)){
			NodeList keyEls = b.getElementsByTagName(bkey);
			if(keyEls.getLength() != 1) return false;
			if(a.get(akey).toString().compareTo(keyEls.item(0).getTextContent().trim())!=0) return false;				
		} else {
			if(b.getElementsByTagName(bkey).getLength() == 1) return false;
		}
		return true;
	}
	
}
