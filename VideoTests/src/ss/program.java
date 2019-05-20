package ss;

import it.sauronsoftware.jave.AudioAttributes;
import it.sauronsoftware.jave.Encoder;
import it.sauronsoftware.jave.EncoderException;
import it.sauronsoftware.jave.EncodingAttributes;
import it.sauronsoftware.jave.InputFormatException;
import it.sauronsoftware.jave.MultimediaInfo;
import it.sauronsoftware.jave.VideoAttributes;
import it.sauronsoftware.jave.VideoInfo;
import it.sauronsoftware.jave.VideoSize;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;





public class program {
	private static final String iframeToken = "http://tenaristv.tenaris.net/tenaristv/www/iFrame.aspx?id=";
	
	public static void main(String[] args) {
		/*
		String text = "<p>\r\n\t&ldquo;With the information we collected from past surveys, we were able to implement actions that have helped the company grow in areas such as leadership, communication, management and work life balance,&rdquo; explained Tenaris Development Director <strong>Gabriela Lopez.</strong> &ldquo;This year, the EOS will be taking place during the context of a global market crisis that is forcing the industry as a whole to redefine itself for the future. Especially during this difficult time, it is extremely important to measure how we are doing in order to focus our efforts on the most critical areas.&rdquo;</p>\r\n<p>\r\n\tTowers Watson, an independent firm, will be processing and coordinating the survey from November 2 to 6. All of Tenaris&rsquo;s salaried employees who have worked at the company for at least three months will be invited to participate. Towers Watson will send out e-mail invitations with a link and personalized password to access. As in previous years, participation in the EOS is voluntary and all responses are confidential.</p>\r\n<div class=\"BoxNewsR\">\r\n\tThis year, a new &ldquo;Individual Engagement&rdquo; section has been added to the Survey. Employees will be able to identify, from a list of options, issues that affect them in terms of motivation and engagement. After the survey has been processed, participants will receive an email suggesting actions they can take individually and/or with their teams, related to their concerns.</div>\r\n<p>\r\n\t&ldquo;The company we are today has been built as a result of the strong contributions employees made during past surveys,&rdquo; continues Lopez. &ldquo;This year participation in the EOS is fundamental so we can all reshape the company we want for the future.&rdquo;</p>\r\n<p>\r\n\tThe survey will include 46 questions that should take no more than 15 minutes to complete. As in previous editions, results will be shared with the Tenaris community once Towers Watson has processed them, and will be used in the design and implementation of initiatives at global, regional, functional and team levels.</p>\r\n<p>\r\n\t<strong>NOTE: </strong>AS part of the survey, eligible employees will receive an e-mail from Tower Watson containing a password and personalized link to the EOS 2015 from the following email account:  <strong>EXTERNAL: Towers Watson - </strong><a href=\"mailto:Techint2015@towerswatson.com\"><strong>Techint2015@towerswatson.com</strong></a></p>\r\n<p>\r\n\t&nbsp;</p>\r\n<p>\r\n\t<iframe frameborder=\"0\" height=\"340\" scrolling=\"no\" src=\"http://tenaristv.tenaris.net/tenaristv/www/iFrame.aspx?id=http://techintgroup.edgetech.com.ar/mediaBank/2/23fa2f1bf0d51f4a381c6206f8d725e3\" width=\"540\"></iframe></p>\r\n";
		int ifix = text.indexOf(iframeToken);
		if(ifix>0){
			String videoBaseUrl = text.substring(ifix + iframeToken.length(), text.indexOf("\"", ifix));
			
			// System.out.print(videoBaseUrl);
			
			
		}
		
		
		System.out.println( "a\\b\\c/d/e".replace("\\", "_").replace("/", "_") );
		*/
		//ResizeVideo();
		
		ToAniGif();
	}
	
	public static void ToAniGif(){
		Process ffmpeg = null;
		final String ffmpegExe = "D:\\Projects\\Tenaris\\Infopoint\\jave-1.0.2-src\\jave-1.0.2-src\\it\\sauronsoftware\\jave\\ffmpeg.exe";
		File source = new File("D:\\temp\\video\\02.mp4");
		File target = new File("D:\\temp\\video\\dest02.gif");
		File palette = new File("D:\\temp\\video\\02_palette.png");
		Integer seconds = 30;
		Integer fps = 10;
		
		/*
		ffmpeg -y -t 30 -i D:\temp\video\02.mp4 -vf scale=320:-1:flags=lanczos,palettegen,fps=13 D:\temp\video\02_palette.png

		ffmpeg -y -t 30 -i D:\temp\video\02.mp4 -i D:\temp\video\02_palette.png -filter_complex "fps=13,scale=320:-1:flags=lanczos[x];[x][1:v]paletteuse" D:\temp\video\02.gif
		*/
		
		String paletteCmd = String.format("%s -y -t %d -i %s -vf scale=-1:-1:flags=lanczos,palettegen,fps=%d %s", 
				ffmpegExe,
				seconds,
				source,
				fps,
				palette
				); 	
		
		String convert = String.format("%s -y -t %d -i %s -i %s -filter_complex \"fps=%d,scale=-1:-1:flags=lanczos[x];[x][1:v]paletteuse\" %s", 
				ffmpegExe,
				seconds,
				source,
				palette,
				fps,
				target);
		
		try {
			Runtime runtime = Runtime.getRuntime();
			ffmpeg = runtime.exec(paletteCmd);
			
			/*
			InputStream in = ffmpeg.getErrorStream();//.getInputStream(); 
			DataInputStream dis = new DataInputStream(in); 
			String disr = dis.readLine(); 
			while ( disr != null ) { 
				System.out.println(disr); disr = dis.readLine(); 
			} 
			*/
			
			int res = ffmpeg.waitFor();
			ffmpeg = runtime.exec(convert);
			res = ffmpeg.waitFor();

		/*
		inputStream = ffmpeg.getInputStream();
		outputStream = ffmpeg.getOutputStream();
		errorStream = ffmpeg.getErrorStream();
		*/

		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			ffmpeg.destroy();
		}
	}
	
		private static final Integer WIDTH = 854;
		private static final Integer HEIGHT = 480;
	
	
		public static void ResizeVideo(){

			String inputfile="D:\\temp\\video\\02.mp4";
			String outputfile="D:\\temp\\video\\02_1.mov";
			
			
			File source = new File("D:\\temp\\video\\video.flv");
			File target = new File("D:\\temp\\video\\video.mp4");
			
			AudioAttributes audio = new AudioAttributes();
			audio.setCodec("libfaac");
			audio.setBitRate(new Integer(128000));
			audio.setSamplingRate(new Integer(44100));
			audio.setChannels(new Integer(2));
			/*
			audio.setCodec("libmp3lame");
			audio.setBitRate(new Integer(64000));
			audio.setChannels(new Integer(1));
			audio.setSamplingRate(new Integer(22050));
			*/
			VideoAttributes video = new VideoAttributes();
			video.setCodec("mpeg4");
			//video.setBitRate(new Integer(160000));
			video.setBitRate(new Integer(250000));
			video.setFrameRate(new Integer(24));
			//video.setFrameRate(new Integer(15));
			video.setSize(new VideoSize(854, 480));
			EncodingAttributes attrs = new EncodingAttributes();
			attrs.setFormat("mp4");
			attrs.setAudioAttributes(audio);
			attrs.setVideoAttributes(video);
			Encoder encoder = new Encoder();
			
			try {
				/*
				MultimediaInfo info = encoder.getInfo(source);
				System.out.println( info.getFormat());
				System.out.println( info.getDuration());
				VideoInfo videoinfo = info.getVideo();
				VideoSize videosize = videoinfo.getSize();
				System.out.println( videoinfo.getDecoder() + " " + videoinfo.getBitRate() + " " + videoinfo.getFrameRate() );
				System.out.println("" + videosize.getWidth() + "x" + videosize.getHeight());
				*/
				encoder.encode(source, target, attrs);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InputFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (EncoderException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
			/*
			ResizeVideoListener resizeListener = new ResizeVideoListener(WIDTH, HEIGHT);
			Resizer resizer = new Resizer(WIDTH, HEIGHT);
	 
			// reader
			IMediaReader reader = ToolFactory.makeReader(inputfile);
			reader.addListener(resizer);
	 
			// writer
			IMediaWriter writer = ToolFactory.makeWriter(outputfile, reader);
			resizer.addListener(writer);
			writer.addListener(resizeListener);

			reader.addListener(ToolFactory.makeViewer(true));
	 
			while (reader.readPacket() == null) { 
				// continue coding
			}
		 	*/
			
		}

	

}
