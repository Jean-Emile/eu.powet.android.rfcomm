package eu.powet.android.rfcomm.thread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.powet.android.rfcomm.listener.ConnectedThreadListener;
import eu.powet.android.rfcomm.listener.TimeoutThreadListener;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 * @author max
 */
public class ConnectedThread extends Thread implements TimeoutThreadListener {
	
	// Debugging
	private static final String TAG = "ConnectedThread";
	private static final boolean D = true;
	
    private final BluetoothSocket mSocket;
    private final InputStream mInStream;
    private final OutputStream mOutStream;
    private final BluetoothDevice mDevice;
    
    private TimeoutThread mTimeoutThread;
    private ConnectedThreadListener listener;

    public ConnectedThread(BluetoothSocket socket, String socketType, long timeout) {
        Log.d(TAG, "create ConnectedThread: " + socketType);
        mSocket = socket;
        mDevice = mSocket.getRemoteDevice();
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the BluetoothSocket input and output streams
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "temp sockets not created", e);
        }

        mInStream = tmpIn;
        mOutStream = tmpOut;
        mTimeoutThread = new TimeoutThread(timeout);
        mTimeoutThread.addListener(this);
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectedThread");
        
        if (listener != null) listener.newDeviceConnected(this);
        
        if (D) Log.i(TAG, "Starting the TimeoutThread for "+mTimeoutThread.getTime()+" milliseconds");
        mTimeoutThread.start();
        
        byte[] buffer = new byte[1024];
        int bytes;

        // Keep listening to the InputStream while connected
        while (true) {
            try {
                // Read from the InputStream
                bytes = mInStream.read(buffer);
                
                resetTimeout();

				if (bytes > 0) {
                    // Send the obtained bytes by the listener
                    if (listener != null) listener.incomingData(mSocket.getRemoteDevice(), bytes, buffer);
				}
                
            } catch (IOException e) {
                Log.e(TAG, "disconnected "+mDevice, e);
                if (listener != null) listener.connectionLost(mDevice);
                break;
            }
        }
    }

    /**
     * Write to the connected OutStream.
     * @param buffer  The bytes to write
     */
    public void write(byte[] buffer) {
        try {
            mOutStream.write(buffer);
            
            resetTimeout();

            // Share the sent message back by the listener
            if (listener != null) listener.sentData(mDevice, buffer);
            
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void cancel() {
        try {
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "close() of connect socket failed", e);
        }
    }
    
    public void addListener(ConnectedThreadListener listener) {
    	this.listener = listener;
    }
    
    public void removeListener() {
    	this.listener = null;
    }
    
    public BluetoothDevice getDevice() {
    	return mDevice;
    }
    
    /**
     * Asks the timeout thread to reset to its initial value
     */
    private void resetTimeout() {
    	// reset the timeout because we received data
        mTimeoutThread.reset();
    }

	@Override
	public void onTimeExpired() {
		if (D) Log.i(TAG, "Timeout: Connection expired with "+mDevice+ ", closing socket...");
		cancel();
	}
}
