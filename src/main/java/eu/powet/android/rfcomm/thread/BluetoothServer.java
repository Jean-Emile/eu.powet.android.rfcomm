package eu.powet.android.rfcomm.thread;

import java.io.IOException;

import eu.powet.android.rfcomm.Rfcomm;
import eu.powet.android.rfcomm.listener.BluetoothServerListener;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * This thread runs while listening for incoming connections. It behaves
 * like a server-side client. It runs until cancelled.
 * @author max
 */
public class BluetoothServer extends Thread {
	
    // Debugging
    private static final boolean D = true;
    private static final String TAG = "BluetoothServer";
	
    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "RfcommSecure";
    private static final String NAME_INSECURE = "RfcommInsecure";
    
	
    // The local server socket
    private final BluetoothServerSocket mServerSocket;
    private String mSocketType;
    private BluetoothAdapter mAdapter;
    private Rfcomm rfcomm;
    private BluetoothServerListener listener;
    private long timeout;

    public BluetoothServer(Rfcomm rfcomm, boolean secure, long timeout) {
    	this.rfcomm = rfcomm;
        BluetoothServerSocket tmp = null;
        mSocketType = secure ? "Secure":"Insecure";
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.timeout = timeout;

        // Create a new listening server socket
        try {
            if (secure) {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                    rfcomm.getSecureUUID());
            } else {
                tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        NAME_INSECURE, rfcomm.getUnsecureUUID());
            }
        } catch (IOException e) {
            Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
        }
        mServerSocket = tmp;
    }

    public void run() {
        if (D) Log.d(TAG, "Socket Type: " + mSocketType +
                "BEGIN mAcceptThread" + this);
        setName("AcceptThread " + mSocketType);

        BluetoothSocket socket = null;

        // Listen to the server socket if we're not connected
        while (true) {
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                socket = mServerSocket.accept();
                
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                break;
            }

            // If a connection was accepted
            if (socket != null) {
                // handle the new remote device in a ConnectedThread
            	ConnectedThread ct = new ConnectedThread(socket, mSocketType, timeout);
            	ct.addListener(rfcomm);
            	ct.start();
            	if (listener != null) listener.newDeviceConnected(ct);
            }
        }
        if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

    }

    public void cancel() {
        if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
        try {
            mServerSocket.close();
            if (listener != null) listener.closed();
            
        } catch (IOException e) {
            Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
        }
    }
    
    public void addListener(BluetoothServerListener listener) {
    	this.listener = listener;
    }
    
    public void removeListener() {
    	this.listener = null;
    }
}
