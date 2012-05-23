package eu.powet.android.rfcomm;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
/**
 * Created by leiko
 * User: max.tricoire@gmail.com
 * Date: 22/05/2012
 * Time: 15:28
 */
public class Rfcomm implements IRfcomm, BluetoothServerListener, ConnectedThreadListener, ConnectThreadListener {       

    // Debugging
    private static final boolean D = true;
    private static final String TAG = "Rfcomm";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "RfcommSecure";
    private static final String NAME_INSECURE = "RfcommInsecure";
    
    // Messages sent by Rfcomm
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received by the Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Unique UUID for this application
    public static final UUID MY_UUID_SECURE =
        UUID.fromString("eae75780-a40f-11e1-b3dd-0800200c9a66");
    public static final UUID MY_UUID_INSECURE =
        UUID.fromString("8a4bc930-9f53-11e1-a8b0-0800200c9a66");
    
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private BluetoothServer mSecureBluetoothServer;
    private BluetoothServer mInsecureBluetoothServer;
    private Map<BluetoothDevice, ConnectThread> connectionAttempts;
    private Map<BluetoothDevice, ConnectedThread> remoteConnections;
    private int mState;
	private Set<BluetoothDevice> devices;
	private EventListenerList listenerList;
	private Context ctx;
	private boolean receiverRegistered = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;			// we're doing nothing
    public static final int STATE_LISTEN = 1;		// now listening for incoming connections
    public static final int STATE_CONNECTING = 2;	// now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;	// now connected to a remote device

	public Rfcomm(Context _ctx, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        this.ctx = _ctx;
        mHandler = handler;
		
		if ((mAdapter != null) && mAdapter.isEnabled()) {
			Log.i(TAG, "Bluetooth adapter found and enabled on device. ");
		} else {
			Log.e(TAG, "Bluetooth adapter NOT FOUND or NOT ENABLED!");
			return;
		}

		listenerList = new EventListenerList();
		devices = new HashSet<BluetoothDevice>();
		connectionAttempts = new Hashtable<BluetoothDevice, ConnectThread>();
		remoteConnections = new Hashtable<BluetoothDevice, ConnectedThread>();
		
		// Register for broadcasts when a device is discovered
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		ctx.registerReceiver(mReceiver, filter);

		filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		ctx.registerReceiver(mReceiver, filter);

		// Register for broadcasts when discovery has finished
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		ctx.registerReceiver(mReceiver, filter);
		receiverRegistered = true;

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();

		// If there are paired devices, add each one to the ArrayAdapter
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				devices.add(device);
			}
		}
	}

	public void addEventListener (BluetoothEventListener listener) {
		listenerList.add(BluetoothEventListener.class, listener);
	}

	public void removeEventListener (BluetoothEventListener listener) {
		listenerList.remove(BluetoothEventListener.class, listener);
	}

	void fireSerialAndroidEvent(BluetoothEvent evt, BluetoothDevice device) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = 0; i < listeners.length; i += 2)
		{

			if (listeners[i] == BluetoothEventListener.class)
			{
				switch (evt.getTypeEvent()) {
					case ACTION_DISCOVERY_FINISHED:
						((BluetoothEventListener) listeners[i + 1]).discoveryFinished(evt);
						break;
						
					case DISCONNECTED:
						((BluetoothEventListener) listeners[i + 1]).disconnected(device);
						break;
						
					case NEW_DEVICE_FOUND:
						((BluetoothEventListener) listeners[i + 1]).newDeviceFound(device);
						break;
						
					case DISCOVERABLE:
						((BluetoothEventListener) listeners[i + 1]).discoverable();
						break;
	
					case CONNECTED:
						((BluetoothEventListener) listeners[i + 1]).connected(device);
						break;
				}

			}
		}
	}
	
	void fireSerialAndroidEvent(BluetoothEvent evt) {
		fireSerialAndroidEvent(evt, null);
	}
	
	@Override
	public void setDiscoverable(int duration) {
        if (mAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
            ctx.startActivity(discoverableIntent);
        }
    }
	
	@Override
	public void setName(String name) {
		mAdapter.setName(name);
	}
	
	@Override
	public void unregisterReceiver() {
	     if (receiverRegistered) {
	    	 ctx.unregisterReceiver(mReceiver);
		     receiverRegistered = false;
	     }
	}
	
	public void discovering(){

		if (mAdapter.isDiscovering()) {
			mAdapter.cancelDiscovery();
		}
		// Request discover from BluetoothAdapter
		mAdapter.startDiscovery();
	}

	public void cancelDiscovery() {
		if (mAdapter.isDiscovering()) mAdapter.cancelDiscovery();
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// Only get not bounded devices
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					devices.add(device);
					fireSerialAndroidEvent(new BluetoothEvent(ctx, TypeEvent.NEW_DEVICE_FOUND), device);
				}				
				
				// When discovery is finished, change the Activity title
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				fireSerialAndroidEvent(new BluetoothEvent(ctx, TypeEvent.ACTION_DISCOVERY_FINISHED));
			}

			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
				fireSerialAndroidEvent(new BluetoothEvent(ctx, TypeEvent.DISCONNECTED));
			}
			
			if (BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE.equals(action)) {
				fireSerialAndroidEvent(new BluetoothEvent(ctx, TypeEvent.DISCOVERABLE));
			}
		}
	};

	@Override
	public Set<BluetoothDevice> getDevices() {
		return devices;
	}
	
	@Override
	public BluetoothDevice getDevice(String address) {
		for (BluetoothDevice d : devices) {
			if (d.getAddress().equals(address)) return d;
		}
		return null;
	}
	
	@Override
	public BluetoothDevice getDeviceByName(String name) {
		for (BluetoothDevice d : devices) {
			if (d.getName().equals(name)) return d;
		}
		return null;
	}
	
    /**
     * Set the current state of the connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the service. Specifically start BluetoothServer to begin a
     * session in listening (server) mode.
     * Calling start() when the service is already started will
     * cancel every active connections
     */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        for (ConnectThread ct : connectionAttempts.values()) ct.cancel();

        // Cancel any thread currently running a connection
        for (ConnectedThread ct : remoteConnections.values()) ct.cancel();

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureBluetoothServer == null) {
            mSecureBluetoothServer = new BluetoothServer(Rfcomm.this, true);
            mSecureBluetoothServer.addListener(this);
            mSecureBluetoothServer.start();
        }
        
        if (mInsecureBluetoothServer == null) {
            mInsecureBluetoothServer = new BluetoothServer(Rfcomm.this, false);
            mSecureBluetoothServer.addListener(this);
            mInsecureBluetoothServer.start();
        }
    }

    /**
     * Start a ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.d(TAG, "connect to: " + device);
        
        // no connection attempt already made
        if (!connectionAttempts.containsKey(device)) {
        	// no active connection already set up
        	if (!remoteConnections.containsKey(device)) {
        		// then we create a new connection
        		ConnectThread ct = new ConnectThread(this, device, secure);
        		ct.addListener(this);
        		ct.start();
        		connectionAttempts.put(device, ct);
        	} else {
        		if (D) Log.d(TAG, "Connection already made with "+device+", abort double connection");	
        	}
        } else {
        	if (D) Log.d(TAG, "Connection attempt to "+device+" already initiated, abort double connection");
        }
        // TODO change the state implementation
        // setState(STATE_CONNECTING);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        for (ConnectThread ct : connectionAttempts.values()) {
            ct.cancel();
        }
        connectionAttempts.clear();

        for (ConnectedThread ct : remoteConnections.values()) {
            ct.cancel();
        }
        remoteConnections.clear();

        if (mSecureBluetoothServer != null) {
            mSecureBluetoothServer.cancel();
            mSecureBluetoothServer = null;
        }

        if (mInsecureBluetoothServer != null) {
            mInsecureBluetoothServer.cancel();
            mInsecureBluetoothServer = null;
        }
        setState(STATE_NONE);
    }
    
	@Override
	public void newDeviceConnected(ConnectedThread connThread) {
		// add the connected device and thread to the map
		remoteConnections.put(connThread.getDevice(), connThread);
		
		// call the listener method to notify
		
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, connThread.getDevice().getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
	}

	@Override
    public void broadcast(byte[] out) {
        if (mState != STATE_CONNECTED) return;
        for (ConnectedThread ct : remoteConnections.values()) ct.write(out);
        mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, out).sendToTarget();
    }
    
	@Override
    public void write(BluetoothDevice device, byte[] out) {
		if (mState != STATE_CONNECTED || !remoteConnections.containsKey(device)) return;
		remoteConnections.get(device).write(out);
		mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, out).sendToTarget();
    }
    
	@Override
    public void writeFromName(String deviceName, byte[] out) {
    	write(getDeviceByName(deviceName), out);
    }
    
	@Override
    public void writeFromAddress(String deviceAddress, byte[] out) {
    	write(getDevice(deviceAddress), out);
    }

	@Override
	public boolean isConnected() {
		switch (mState) {
		case STATE_CONNECTED:
			return true;
		default:
			return false;
		}
	}

	@Override
	public String getMyAddress() {
		return mAdapter.getAddress();
	}

	@Override
	public void connectionFailed(BluetoothDevice device) {
		// remove the device from the connectionAttempts
		connectionAttempts.remove(device);
		
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device ("+device.getAddress()+")");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
	}

	@Override
	public void connectionLost(BluetoothDevice device) {
		// remove the device from the remoteConnections
		remoteConnections.remove(device);
		
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device connection was lost ("+device.getAddress()+")");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
	}

	@Override
	public void incomingData(BluetoothDevice device, int bytes, byte[] buffer) {
		// Send the obtained bytes to the UI Activity
        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
	}

	@Override
	public void sentData(BluetoothDevice device, byte[] buffer) {
        // Share the sent message back to the UI Activity
//        mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
	}

	@Override
	public void closed() {
		setState(STATE_NONE);
	}
}
