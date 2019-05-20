package ss;

import it.sauronsoftware.jave.AudioAttributes;
import it.sauronsoftware.jave.Encoder;
import it.sauronsoftware.jave.EncodingAttributes;
import it.sauronsoftware.jave.MultimediaInfo;
import it.sauronsoftware.jave.VideoAttributes;
import it.sauronsoftware.jave.VideoSize;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * Servlet implementation class MongoFileServlet
 */
public class FileUpload extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static Log log = LogFactory.getLog(FileUpload.class);

	//protected static final String mediaPath = "D:\\Appls\\Java\\wso2as-5.2.1\\repository\\deployment\\server\\webapps\\infopoint\\media\\";
	private String mediaPath;
	private String ffmpegExe;
	protected AudioAttributes audio;
	protected VideoAttributes video;
	protected EncodingAttributes attrs;
	protected static final Integer WIDTH = 854;
	protected static final Integer HEIGHT = 480;
	
	private final List<String> videoExtensions = Arrays.asList("mov", "mp4", "flv", "ogg", "vob", "mpeg", "avi", "wmv");
	
	private ServletConfig config;

    /**
     * Default constructor. 
     */
    public FileUpload() {
    	// OnDel... 
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
	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		this.config = config;
		mediaPath = this.config.getInitParameter("mediaPath");
		ffmpegExe = this.config.getInitParameter("ffmpegExe");
	}
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");		
		response.addHeader("Access-Control-Allow-Headers", "Content-Type, Content-Range, Content-Disposition, Content-Description");
		response.addHeader("Allow", "GET, POST, HEAD, OPTIONS");
        response.setStatus(HttpServletResponse.SC_OK);	
	}
	
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");		
		response.addHeader("Access-Control-Allow-Headers", "Content-Type, Content-Range, Content-Disposition, Content-Description");
		response.addHeader("Allow", "GET, POST, HEAD, OPTIONS");
        response.setStatus(HttpServletResponse.SC_OK);
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		response.setContentType("text/html;charset=UTF-8");
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");		
		response.addHeader("Access-Control-Allow-Headers", "Content-Type, Content-Range, Content-Disposition, Content-Description");
	
		
	    final PrintWriter writer = response.getWriter();
	    OutputStream out = null;
	    InputStream filecontent = null;
	    
	    if (!ServletFileUpload.isMultipartContent(request)) {
            throw new IllegalArgumentException("Request is not 'multipart/form-data'.");
        }

        ServletFileUpload uploadHandler = new ServletFileUpload(new DiskFileItemFactory());
        try {
			final String nId = request.getParameter("Id");
	    	final String destPath =  mediaPath + "orig" + File.separator + nId;
	    	initVideoTranscoder();
	    	StringWriter sw = new StringWriter();
	    	
            List<FileItem> items = uploadHandler.parseRequest(request);
            for (FileItem item : items) {
                if (!item.isFormField()) {
                 	File fDestPath = new File(destPath);
    		    	fDestPath.mkdirs();
    		    	String fileName = new File(item.getName()).getName();
    		    	
    		    	File destFile = new File(destPath + File.separator + fileName);
    		    	if(destFile.exists()){
    		    		// remove old
    		    		destFile.delete();
    		    	}
    		    	
    		    	// Extract animated gif if video, resample video directly, 
                    item.write(destFile);
                    String[] parts = fileName.split("\\.");       
                    String ext = parts[parts.length-1];
                    String nameOnly = parts[0];
                    if(videoExtensions.contains(ext.toLowerCase())){
                    	// it's a video. Convert and resample here 
                    	// -> original: <media>/orig/id/filename.ext
                    	// -> dest video: <media>/orig/id/filename_v.mp4
                    	// -> dest palette: <media>/orig/id/filename_p.png
                    	// -> dest anigif: <media>/orig/id/filename_a.gif
                    	/*
                    	ffmpeg -y -t 30 -i D:\temp\video\02.mp4 -vf scale=320:-1:flags=lanczos,palettegen,fps=13 D:\temp\video\02_palette.png

						ffmpeg -t 30 -i D:\temp\video\02.mp4 -i D:\temp\video\02_palette.png -filter_complex "fps=13,scale=320:-1:flags=lanczos[x];[x][1:v]paletteuse" D:\temp\video\02.gif
						
						
						ffmpeg -v warning -t 30 -r 5 -i D:\temp\video\02.mp4 -gifflags +transdiff -y D:\temp\video\02.gif
						
						-vf scale=300:-1
                    	*/
                    	Encoder encoder = new Encoder();
    					encoder.encode(destFile, new File(destPath + File.separator + nameOnly + "_v.mp4"), attrs);
    					
    					
    					
    					toAniGif(encoder.getInfo(destFile), destFile, new File(destPath + File.separator + nameOnly + "_a.gif"), new File(destPath + File.separator + nameOnly + "_p.png"), new PrintWriter(sw));		
                    }
                }
            }
            response.setStatus(HttpServletResponse.SC_OK);
            // sw.toString().toJSON
	        writer.write("{\"error\":null}");
	    } catch (FileNotFoundException fne) {
	        log.error(String.format("Problems during file upload. Error: %s", fne.getMessage()));
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            //writer.write(String.format("{\"error\":\"%s\" }", fne.getMessage()));
            fne.printStackTrace(writer);
	    } catch (Exception e){
	        log.error(String.format("Error: %s", e.getMessage()));
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            //writer.write(String.format("{\"error\":\"%s\" }", e.getMessage()));		    	
            e.printStackTrace(writer);
	    } finally {
	        if (out != null) {
	            out.close();
	        }
	        if (filecontent != null) {
	            filecontent.close();
	        }
	        if (writer != null) {
	            writer.close();
	        }
	    }

	}
	
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");		
		response.addHeader("Access-Control-Allow-Headers", "Content-Type, Content-Range, Content-Disposition, Content-Description");		
	}
	
	@SuppressWarnings("deprecation")
	protected void toAniGif(MultimediaInfo info, File source, File target, File palette, PrintWriter writer) throws Exception{
		
		
		Integer seconds = 30;
		Integer fps = 10;
		Process ffmpeg = null;
		Integer width = 400;

		if(info.getDuration() < seconds) { seconds = (int)info.getDuration(); }
		Integer videoWidth = info.getVideo().getSize().getWidth(); 
		if(videoWidth < width) { width = videoWidth; }
		if(info.getVideo().getFrameRate() < fps)  { fps = (int)info.getVideo().getFrameRate(); }
		
		String paletteCmd = String.format("%s -y -t %d -i \"%s\" -vf scale=%d:-1:flags=lanczos,palettegen,fps=%d \"%s\"", 
				ffmpegExe,
				seconds,
				source,
				width,
				fps,
				palette
				); 	
		
		
//writer.println("Palette: " + paletteCmd);
		
		String convert = String.format("%s -y -t %d -i \"%s\" -i \"%s\" -filter_complex \"fps=%d,scale=%d:-1:flags=lanczos[x];[x][1:v]paletteuse\" \"%s\"", 
				ffmpegExe,
				seconds,
				source,
				palette,
				fps,
				width,
				target);

//writer.println("Convert: " + convert);

		try {
			Runtime runtime = Runtime.getRuntime();
//writer.println("Starting palette execution");
			ffmpeg = runtime.exec(paletteCmd);
			
			
	InputStream in = ffmpeg.getErrorStream();//.getInputStream(); 
	DataInputStream dis = new DataInputStream(in); 
	String disr = dis.readLine(); 
	while ( disr != null ) { 
		writer.println(disr); disr = dis.readLine(); 
	} 
		
			
//			ffmpeg.waitFor(1, TimeUnit.MINUTES);
// writer.println("Starting conversion execution");
			ffmpeg = runtime.exec(convert);


//			 ffmpeg.waitFor(2, TimeUnit.MINUTES);

	 in = ffmpeg.getErrorStream();//.getInputStream(); 
	 dis = new DataInputStream(in); 
	 disr = dis.readLine(); 
	while ( disr != null ) { 
		writer.println(disr); disr = dis.readLine(); 
	} 


		} catch (Exception e) {
			log.error("Error generating animated gif.", e);
			e.printStackTrace(writer);
			throw e;
		} finally {
			ffmpeg.destroy();
		}
	}	
}
