package eu.powet.android.rfcomm;

import java.io.IOException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

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

    public ConnectThread(Rfcomm rfcomm, BluetoothDevice device, boolean secure) {
    	this.rfcomm = rfcomm;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    	mDevice = device;
        BluetoothSocket tmp = null;
        mSocketType = secure ? "Secure" : "Insecure";

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
            if (secure) {
                tmp = device.createRfcommSocketToServiceRecord(
                        Rfcomm.MY_UUID_SECURE);
            } else {
                tmp = device.createInsecureRfcommSocketToServiceRecord(
                        Rfcomm.MY_UUID_INSECURE);
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
    	ConnectedThread ct = new ConnectedThread(mSocket, mSocketType);
    	ct.addListener(rfcomm);
    	ct.start();
    	if (listener != null) listener.newDeviceConnected(ct);
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
