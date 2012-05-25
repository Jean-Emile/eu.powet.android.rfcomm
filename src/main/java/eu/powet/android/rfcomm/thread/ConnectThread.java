package eu.powet.android.rfcomm.thread;

import java.io.IOException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import eu.powet.android.rfcomm.Rfcomm;
import eu.powet.android.rfcomm.listener.ConnectThreadListener;

/**
 * This thread runs while attempting to make an outgoing connection
 * with a device. It runs straight through; the connection either
 * succeeds or fails.
 * @author max
 *
 */
public class ConnectThread extends Thread {
	
	// Debugging
	private static final String TAG = "ConnectThread";
	private static final boolean D = true;
	
    private final BluetoothSocket mSocket;
    private final BluetoothDevice mDevice;
    private String mSocketType;
    private BluetoothAdapter mAdapter;
    private ConnectThreadListener listener;
    private Rfcomm rfcomm;
    private long timeout;

    public ConnectThread(Rfcomm rfcomm, BluetoothDevice device, boolean secure, long timeout) {
    	this.rfcomm = rfcomm;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    	mDevice = device;
        BluetoothSocket tmp = null;
        mSocketType = secure ? "Secure" : "Insecure";
        this.timeout = timeout;

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
            if (secure) {
                tmp = device.createRfcommSocketToServiceRecord(
                        rfcomm.getSecureUUID());
            } else {
                tmp = device.createInsecureRfcommSocketToServiceRecord(
                        rfcomm.getUnsecureUUID());
            }
        } catch (IOException e) {
            Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
        }
        mSocket = tmp;
    }

    public void run() {
        Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
        setName("ConnectThread" + mSocketType);

        // Always cancel discovery because it will slow down a connection
        mAdapter.cancelDiscovery();

        // Make a connection to the BluetoothSocket
        try {
            // This is a blocking call and will only return on a
            // successful connection or an exception
            mSocket.connect();
        } catch (IOException e) {
            // Close the socket
            try {
                mSocket.close();
                
            } catch (IOException e2) {
                Log.e(TAG, "unable to close() " + mSocketType +
                        " socket during connection failure", e2);
            }
            if (listener != null) listener.connectionFailed(mDevice);
            return;
        }

        // handle the new remote device in a ConnectedThread
    	ConnectedThread ct = new ConnectedThread(mSocket, mSocketType, timeout);
    	ct.addListener(rfcomm);
    	ct.start();
    }

    public void cancel() {
        try {
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
        }
    }
    
    public void addListener(ConnectThreadListener listener) {
    	this.listener = listener;
    }
    
    public void removeListener() {
    	this.listener = null;
    }
    
}
