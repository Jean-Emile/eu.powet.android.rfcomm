package eu.powet.android.rfcomm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 * @author max
 */
public class ConnectedThread extends Thread {
	
	// Debugging
	private static final String TAG = "ConnectedThread";
	private static final boolean D = true;
	
    private final BluetoothSocket mSocket;
    private final InputStream mInStream;
    private final OutputStream mOutStream;
    private final BluetoothDevice mDevice;
    
    private ConnectedThreadListener listener;

    public ConnectedThread(BluetoothSocket socket, String socketType) {
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
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectedThread");
        byte[] buffer = new byte[1024];
        int bytes;

        // Keep listening to the InputStream while connected
        while (true) {
            try {
                // Read from the InputStream
                bytes = mInStream.read(buffer);

				if (bytes > 0) {
                    // Send the obtained bytes by the listener
                    if (listener != null) listener.incomingData(mSocket.getRemoteDevice(), bytes, buffer);
				}
                
            } catch (IOException e) {
                Log.e(TAG, "disconnected", e);
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
}
