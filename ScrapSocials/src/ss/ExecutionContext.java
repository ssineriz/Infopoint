package ss;

public class ExecutionContext {
	private boolean refreshImages;

	public boolean isRefreshImages() {
		return refreshImages;
	}

	public ExecutionContext setRefreshImages(boolean refreshImages) {
		this.refreshImages = refreshImages;
		return this;
	}
	
}
