package ss;

import it.sauronsoftware.jave.FFMPEGLocator;

public class MyFFMPegLocator extends FFMPEGLocator {

	@Override
	protected String getFFMPEGExecutablePath() {
		return "D:\\Projects\\Tenaris\\Infopoint\\jave-1.0.2-src\\jave-1.0.2-src\\it\\sauronsoftware\\jave\\ffmpeg.exe";
	}

}
