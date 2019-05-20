package ss;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// import org.apache.commons.logging.Log;
// import org.apache.commons.logging.LogFactory;

public class StatsServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	// private static Log log = LogFactory.getLog(StatsServlet.class);

	private String statsPath;
	
	private ServletConfig config;

    /**
     * Default constructor. 
     */
    public StatsServlet() {
    	// OnDel... 
    }

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		this.config = config;
		statsPath = this.config.getInitParameter("statsPath");
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
		String filename = request.getRequestURI();
		filename = filename.substring(filename.indexOf("/stats/") + 7);
    	final String destPath =  statsPath + File.separator + filename;
		OutputStream outStream = response.getOutputStream();
    	try{
    		File srcFile = new File(destPath);
    		FileInputStream inStream = new FileInputStream(srcFile);
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
            inStream.close();
            outStream.close();     
    	} catch (Exception ex){
    		ex.printStackTrace(new PrintStream(outStream));
    	}
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
        response.setStatus(HttpServletResponse.SC_OK);	
	}
	
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");		
		response.addHeader("Access-Control-Allow-Headers", "Content-Type, Content-Range, Content-Disposition, Content-Description");		
	}
}
