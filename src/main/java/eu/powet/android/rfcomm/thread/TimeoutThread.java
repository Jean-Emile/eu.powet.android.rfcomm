package eu.powet.android.rfcomm.thread;

import android.util.Log;
import eu.powet.android.rfcomm.listener.TimeoutThreadListener;

public class TimeoutThread extends Thread {

	// Debugging
	private static final String TAG = "TimeoutThread";
	private static final boolean D = true;
	
	private long mTime;
	private TimeoutThreadListener listener;
	
	public TimeoutThread(long time) {
		this.mTime = time;
	}
	
	@Override
	public void run() {
		if (D) Log.i(TAG, "BEGIN TimeoutThread (remaining time="+mTime+")");
		setName("TimeoutThread (time="+mTime+")");
		
		while (true) {
			try {
				Thread.sleep(mTime);
				break;
				
			} catch (InterruptedException e) {
				if (D) Log.i(TAG, "TimeoutThread reseted, starting over again");
			}
		}
		
		if (listener != null) listener.onTimeExpired();
		if (D) Log.i(TAG, "END TimeoutThread");
	}
	
	/**
	 * @return the setted time for the timer
	 */
	public long getTime() {
		return mTime;
	}
	
	/**
	 * Reset the timer
	 */
	public void reset() {
		interrupt();
	}
	
	public void addListener(TimeoutThreadListener listener) {
		this.listener = listener;
	}
	
	public void removeListener() {
		this.listener = null;
	}
}
