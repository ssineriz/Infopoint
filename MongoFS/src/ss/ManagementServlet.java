package ss;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Servlet implementation class ManagementServlet
 */
public class ManagementServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Log log = LogFactory.getLog(ManagementServlet.class);
	
	private ServletConfig config;
	private String restartCmd;
	private String storeKey;
	/**
     * @see HttpServlet#HttpServlet()
     */
    public ManagementServlet() {
        super();
    }
    
	public void init(ServletConfig servletConfig) throws ServletException {
		this.config = servletConfig;
		restartCmd = config.getInitParameter("restartcmd");
		storeKey = config.getInitParameter("storekey");
	}
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// should maybe study some kind of authentication...
		String lclCmd = request.getParameter("cmd");
		PrintWriter out = response.getWriter();
		if(lclCmd!=null && lclCmd!=""){
			try{
				response.getWriter().println("RUNNING " + lclCmd + "<br/>");
				Process p = Runtime.getRuntime().exec(lclCmd);
				
				InputStream in = p.getInputStream(); 
				DataInputStream dis = new DataInputStream(in); 
				String disr = dis.readUTF();
				
				while ( disr != null ) { 
					out.println(disr); 
					disr = dis.readUTF(); 
				}
			
			} catch (Exception ex){
				log.warn(ex); 
				out.println("ERROR : " + ex.toString());
				ex.printStackTrace(out);
			}
			return;
		}
		
		String ip = request.getParameter("restart");
		String storekey = String.format(storeKey, ip);
		String cmd = String.format(restartCmd, ip);
		response.addHeader("Access-Control-Allow-Origin", "*");
		try{
			response.getWriter().println("RUNNING " + storekey + "<br/>");
			Process p = Runtime.getRuntime().exec(storekey);
			 
			InputStream in = p.getInputStream(); 
			DataInputStream dis = new DataInputStream(in); 
			String disr = dis.readUTF(); 
			while ( disr != null ) { 
				response.getWriter().println(disr);
				disr = dis.readUTF(); 
			} 
		} catch (Exception ex){
			log.warn(ex);
			response.getWriter().println("ERROR : " + ex.toString());
		}
		response.getWriter().println("<br/>");
		try{
			response.getWriter().println("RUNNING " + cmd + "<br/>");
			Process p = Runtime.getRuntime().exec(cmd);
			
			InputStream in = p.getInputStream(); 
			DataInputStream dis = new DataInputStream(in); 
			String disr = dis.readUTF(); 
			while ( disr != null ) { 
				response.getWriter().println(disr); 
				disr = dis.readUTF(); 
			} 
		} catch (Exception ex){
			log.warn(ex);
			response.getWriter().println("ERROR : " + ex.toString());
		}
	}

}
