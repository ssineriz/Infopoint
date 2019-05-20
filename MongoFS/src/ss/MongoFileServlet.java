package ss;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;

/**
 * Servlet implementation class MongoFileServlet
 */
public class MongoFileServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private ServletConfig config;
	private DB mongodb;
	private GridFS mongogfs;
    /**
     * Default constructor. 
     */
    public MongoFileServlet() {
        
    }

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		this.config = config;
		Mongo mongo;
		try {
			mongo = new Mongo(config.getInitParameter("mongoserver"), Integer.parseInt(config.getInitParameter("mongoport")));
			mongodb = mongo.getDB(config.getInitParameter("mongodb"));
			if(config.getInitParameter("mongouser")!=null){
				mongodb.authenticate(config.getInitParameter("mongouser"), config.getInitParameter("mongopass").toCharArray());
			}
			mongogfs = new GridFS(mongodb, config.getInitParameter("mongobucket"));
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String filename = request.getRequestURI();
		filename = filename.substring(filename.indexOf("/files/") + 7); // "/files/".length()
		GridFSDBFile image = mongogfs.findOne(filename); // .replace("%20", " ")

		if(image == null ){
			PrintWriter out = response.getWriter();
			out.format(Locale.ITALIAN, "Server %s:%s/%s/%s", config.getInitParameter("mongoserver"), 
					config.getInitParameter("mongoport"),
					config.getInitParameter("mongodb"),
					config.getInitParameter("mongobucket")
					);
			out.print("<br/>");
			out.format(Locale.ITALIAN, "No file found with name %s", filename);
		} else {
			/*
		    if (image.get("contentType") != null) {
		    	response.setContentType(image.get("contentType").toString());
		    } else {
		    	// should guess by extension
		    	String ext = "";

		    	int i = filename.lastIndexOf('.');
		    	if (i > 0) {
		    	    ext = filename.substring(i+1).toLowerCase();
		    	}
		    	switch(ext){
		    		case "mp4":
		    			response.setContentType( "video/mp4");
		    			break;
			    	default:
			    		response.setContentType( "application/octet-stream");
		    	}
		    }
		    
		    response.setContentLength((int)image.getLength());
		    */
			image.writeTo(response.getOutputStream());
			
		}

	}

}
