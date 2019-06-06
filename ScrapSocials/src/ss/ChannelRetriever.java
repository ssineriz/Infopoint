package ss;

import it.sauronsoftware.jave.AudioAttributes;
import it.sauronsoftware.jave.Encoder;
import it.sauronsoftware.jave.EncoderException;
import it.sauronsoftware.jave.EncodingAttributes;
import it.sauronsoftware.jave.InputFormatException;
import it.sauronsoftware.jave.VideoAttributes;
import it.sauronsoftware.jave.VideoSize;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.glxn.qrgen.core.image.ImageType;
import net.glxn.qrgen.javase.QRCode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
/*
import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
*/
// TODO: move to xuggle
public abstract class ChannelRetriever {
	protected final Log log = LogFactory.getLog(ChannelRetriever.class);
	protected String channel;
	protected String channelName;
	protected String channelId;
	protected int channelTypeId;
	protected String channelType; 	
	protected CharsetDecoder utf8Decoder = Charset.forName("UTF-8").newDecoder();
	protected DefaultHttpClient client;
	protected DateFormat outdf;
	protected boolean shouldResample = false;
	
	protected Mongo mongo;
	protected DB mongoDb;
	protected DBCollection postsCollection;
	protected GridFS gfsPhoto;

	protected static final Integer WIDTH = 854;
	protected static final Integer HEIGHT = 480;
	protected static final String mediaPath = "D:\\Appls\\Java\\wso2as-5.2.1\\repository\\deployment\\server\\webapps\\infopoint\\media\\";
	
	protected AudioAttributes audio;
	protected VideoAttributes video;
	protected EncodingAttributes attrs;
	
	public ChannelRetriever(JSONObject channel) {
		this(
				channel.get("ChannelUrl").toString(), 
				channel.get("Id").toString(), 
				channel.get("ChannelName").toString(), 
				Integer.parseInt(((JSONObject) channel.get("ChannelType")).get("TypeId").toString()), 
				((JSONObject) channel.get("ChannelType")).get("Title").toString());
	}
	
	public ChannelRetriever(String channel, String channelId, String channelName, int channelTypeId, String channelType){
		this.channel = channel;
		this.channelId = channelId;
		this.channelName = channelName;
		this.channelTypeId = channelTypeId;
		this.channelType = channelType;
	   	client = new DefaultHttpClient();
	   	outdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
	}
	
	protected void initVideoTranscoder(){
		audio = new AudioAttributes();
		audio.setCodec("libfaac");
		audio.setBitRate(new Integer(128000));
		audio.setSamplingRate(new Integer(44100));
		audio.setChannels(new Integer(2));
		video = new VideoAttributes();
		video.setCodec("mpeg4");
		video.setBitRate(new Integer(250000));
		video.setFrameRate(new Integer(24));
		video.setSize(new VideoSize(WIDTH, HEIGHT));
		attrs = new EncodingAttributes();
		attrs.setFormat("mp4");
		attrs.setAudioAttributes(audio);
		attrs.setVideoAttributes(video);
	}
	
	protected void initMongoDB() throws UnknownHostException{
    	mongo = new Mongo("daleuwis106.dalmine.techint.net:27017");
    	mongoDb = mongo.getDB("infopoint");
    	postsCollection = mongoDb.getCollection("posts");
    	mongoDb.authenticate("wso2", "wso2".toCharArray());
    	gfsPhoto = new GridFS(mongoDb, "photo");
	}
	
	public abstract void execute(ExecutionContext ctx) throws IOException, ClientProtocolException, ParseException;
	
	protected JSONObject getJson(String url)throws IOException, ParseException {
		return getJson(url, null);
	}
	
	protected JSONObject getJson(String url, HashMap<String, String> headers) throws IOException, ParseException{
     	HttpGet request = new HttpGet(url);
     	if(headers!=null){
     		for(String h: headers.keySet())
     		request.addHeader(h, headers.get(h));
     	}
    	HttpResponse response = client.execute(request);
    	StatusLine status =  response.getStatusLine();
    	if(status.getStatusCode()!=HttpStatus.SC_OK){
    		String error = "Http response " + status.getStatusCode() + ":" + status.getReasonPhrase() + " for channel " + channelType + "-" + channelName;
    		log.error(error);
    		throw new IOException(error);
    	}    	    	
    	BufferedReader rd = new BufferedReader (new InputStreamReader(response.getEntity().getContent(), utf8Decoder));
    	return (JSONObject) new JSONParser().parse(rd);

	}
	
	protected String replaceAnchors(Object html){
		if(html == null) return null;
		Document doc = Jsoup.parse(html.toString());  //(input, "UTF-8");

		Elements links = doc.select("a[href]");
		Iterator<Element> iterator = links.iterator();
		while (iterator.hasNext()) {
		  Element link = iterator.next();
		  String url = link.attr("href");
		  // check url makes sense (eg: hash, javascript, etc...)
		  Element bold = doc.createElement("b").appendText(link.text());
		  link.replaceWith(bold);
		  if(url.toLowerCase().startsWith("http") || url.toLowerCase().startsWith("mailto")){
			  // bold.after(" [" + url + "]");
			  String md5 = null;
			  try {
				  MessageDigest m = MessageDigest.getInstance("MD5");
				  m.update(url.getBytes(),0,url.length());
				  md5 = new BigInteger(1,m.digest()).toString(16);
			  } catch (NoSuchAlgorithmException e) {
				  log.error("Error geting MD5 hash", e);
			  }
			  String targetPath = mediaPath + "qrcodes\\" + md5 + ".gif";
			  File qr =  new File(targetPath);
			  if(!qr.exists()){
				try {
					FileOutputStream fup = new FileOutputStream(qr, true);
					QRCode.from(url).to(ImageType.GIF).withSize(250, 250).writeTo(fup);
				} catch (FileNotFoundException e) {
					log.error("Error writing qrcode file", e);
				}
			  }
			  Element img = doc.createElement("img").attr("src", "media/qrcodes/" + md5 + ".gif");
			  doc.appendChild(img);
		  }
		  
		}
		
		return doc.html();
	}
	
	protected long copyImage(ExecutionContext ctx, String objKey, BasicDBObject oPost, String key, Object imgPath) throws IOException,
			ClientProtocolException {
		long resp = 0;
		if(imgPath==null) return resp;
		try{
		String fullPath = imgPath.toString();
		String[] parts = fullPath.split("/");
		String fileName = parts[parts.length-1].split("\\?")[0].replaceAll(" ", "%20");
		if("imagesnoImage".compareToIgnoreCase(fileName)==0){
			return resp;
		}
		String imgKey = objKey + "/" + key + "/" + fileName;
//		log.info("Getting " + imgKey);
//		long startNano = System.nanoTime();
		List<GridFSDBFile> olds = gfsPhoto.find(imgKey);
		if(olds.isEmpty() || ctx.isRefreshImages()){
			HttpGet req=null;
			try {
				req = new HttpGet(new URIBuilder(fullPath.replaceAll(" ", "%20")).build());
			} catch (URISyntaxException e) {
				log.error(e);
				oPost.removeField(key + "_local");
				return resp;
			}
			HttpResponse fresp = client.execute(req);
			if(fresp.getStatusLine().getStatusCode() != HttpStatus.SC_OK){
				// failed loading image
	    		log.error("Http response " + fresp.getStatusLine().getStatusCode() + ":" + fresp.getStatusLine().getReasonPhrase() + " for image " + fullPath + " in channel " + channelType + "-" + channelName);
	    		oPost.removeField(key + "_local");
	    		req.releaseConnection();
			} else {
				String imgExt = ".jpg";
				int eix = fileName.lastIndexOf('.');
				if (eix > 0) {
					imgExt = fileName.substring(eix);
				}
				InputStream initialStream = fresp.getEntity().getContent();
				if(shouldResample && ".jpg.gif.tif.tiff.png.jpeg".indexOf(imgExt) >= 0) {
					// Temp file for image.
					File temp = File.createTempFile("img", imgExt); 
				    OutputStream tempStream = new FileOutputStream(temp);
				 
				    byte[] buffer = new byte[8 * 1024];
				    int bytesRead;
				    while ((bytesRead = initialStream.read(buffer)) != -1) {
				    	tempStream.write(buffer, 0, bytesRead);
				    }
				    tempStream.close();
				    FileInputStream imgStream = null;
				    File tempDs = null;
					// Check image size (bytes, width, height, dpi), and clash to [MAX_WIDTH, MAX_HEIGHT, MAX_DPI (72)]
					javaxt.io.Image image = new javaxt.io.Image(temp);
					if(image.getHeight() > HEIGHT || image.getWidth() > WIDTH) {
						tempDs = File.createTempFile("img", imgExt);
						if( (image.getHeight() / HEIGHT) > (image.getWidth() / WIDTH) && image.getWidth() > WIDTH ) {
							image.resize(WIDTH, HEIGHT, true);						
						} else {
							image.setHeight(HEIGHT);
						}
						image.setOutputQuality(100f);
						image.saveAs(tempDs);
						imgStream = new FileInputStream(tempDs);
					} else {
						imgStream = new FileInputStream(temp);
					}
					GridFSInputFile gfsFile = gfsPhoto.createFile(imgStream, imgKey, true);
					gfsFile.save();
					imgStream.close();
					resp = gfsFile.getLength();
	//				log.info("File " + imgKey + ", Length:" + resp + ", "+ (resp*1000000/(System.nanoTime() - startNano))  + "KB/s");
					oPost.put(key + "_local", imgKey);
					temp.deleteOnExit();
					if(tempDs != null) {
						tempDs.deleteOnExit();
					}
				} else {
					// no resample					
					GridFSInputFile gfsFile = gfsPhoto.createFile(fresp.getEntity().getContent(), imgKey, true);
					gfsFile.save();
					resp = gfsFile.getLength();
	//				log.info("File " + imgKey + ", Length:" + resp + ", "+ (resp*1000000/(System.nanoTime() - startNano))  + "KB/s");
					oPost.put(key + "_local", imgKey);
				}
			}
		} else {			
			resp = olds.get(0).getLength();
//			log.debug("Image present " + imgKey + ", Size: " + resp);			
			oPost.put(key + "_local", imgKey);
		}
		} catch (Exception ex){
			log.error(ex);
		}
		return resp;
	}
	
	protected long copyVideo(ExecutionContext ctx, String objKey, BasicDBObject oPost, String key, Object videoPath)
			throws IOException, ClientProtocolException, FileNotFoundException {
		long resp = 0;
		if(videoPath!=null){
			String vidPath = videoPath.toString();

			String vidKey = objKey.replace("\\", "_").replace("/", "_") + "_v.mp4"; 
			
			String[] parts = vidPath.split("/");
			String vidFileName = parts[parts.length-1].split("\\?")[0];
			String vidExt = ".mov";
			int eix = vidFileName.lastIndexOf('.');
			if (eix > 0) {
				vidExt = vidFileName.substring(eix);
			}
			//List<GridFSDBFile> olds = gfsPhoto.find(vidKey);
			//if(olds.isEmpty() || ctx.isRefreshImages()){
			File vidFile = new File(mediaPath + vidKey);

//			log.info("Getting " + vidKey);
//			long startNano = System.nanoTime();
			
			if(!vidFile.exists() || ctx.isRefreshImages()){
				HttpGet req = new HttpGet(vidPath);
				HttpResponse fresp = client.execute(req);
				if(fresp.getStatusLine().getStatusCode()!=HttpStatus.SC_OK){
					// failed loading image
		    		log.error("Http response " + fresp.getStatusLine().getStatusCode() + ":" + fresp.getStatusLine().getReasonPhrase() + " for video " + vidPath + " in channel " + channelType + "-" + channelName);
		    		oPost.removeField(key + "_local");
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

				    // Sauronsoftware way
				    
					Encoder encoder = new Encoder();

					try {
						encoder.encode(temp, vidFile, attrs);
					    temp.deleteOnExit();
						oPost.put(key + "_local", vidKey);
						resp = vidFile.length();
//						log.info("File " + vidKey + ", Length:" + resp + ", "+ (resp*1000000/(System.nanoTime() - startNano))  + "KB/s");					
					} catch (IllegalArgumentException e) {
						log.error("Video conversion", e);
					} catch (InputFormatException e) {
						log.error("Video conversion", e);
					} catch (EncoderException e) {
						log.error("Video conversion", e);
					}
					
					// Xuggler way (must complete)
				    /*
				    try {
				    	long startNano = System.nanoTime();
					    IMediaReader reader = ToolFactory.makeReader(temp.getAbsolutePath());
					    IMediaWriter writer = ToolFactory.makeWriter(vidFile.getAbsolutePath());
					    writer.addVideoStream(arg0, arg1, arg2, arg3, arg4)
					    writer.addAudioStream(arg0, arg1, arg2, arg3, arg4)
					    reader.addListener(writer);
					    while (reader.readPacket() == null)
					      ;
					    temp.deleteOnExit();
						oPost.put(key + "_local", vidKey);
						resp = vidFile.length();				    
						log.info("File " + vidKey + ", Length:" + resp + ", "+ (resp*1000000/(System.nanoTime() - startNano))  + "KB/s");					
				    } catch (){
				    	
				    }
				    */
				}
			} else {
				resp = vidFile.length();
				oPost.put(key + "_local", vidKey);
			}
		}
		return resp;
	}

	protected Boolean areKeysEqual(DBObject a, JSONObject b, String key){
		if(a.containsField(key)){
			if(!b.containsKey(key)) return false;
			if(a.get(key).toString().compareTo(b.get(key).toString())!=0) return false;				
		} else {
			if(b.containsKey(key)) return false;
		}
		return true;
	}
		
}
