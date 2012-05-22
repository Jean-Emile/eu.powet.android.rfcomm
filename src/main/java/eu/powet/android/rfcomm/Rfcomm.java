package eu.powet.android.rfcomm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
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
public class Rfcomm implements IRfcomm {       

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
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("eae75780-a40f-11e1-b3dd-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
        UUID.fromString("8a4bc930-9f53-11e1-a8b0-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
	private Set<BluetoothDevice> devices=null;
	private EventListenerList listenerList=null;
	private Context ctx=null;
	private final int SIZE_BUFFER =1024;
	private ByteFIFO fifo_data_read;
	private boolean receiverRegistered = false;

    // Enum that indicate the current connection state
    public static final int STATE_NONE = 0;		// we're doing nothing
    public static final int STATE_LISTEN = 1;		// now listening for incoming connections
    public static final int STATE_CONNECTING = 2;	// now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;	// now connected to a remote device

	public Rfcomm(Context _ctx, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        this.ctx = _ctx;
        mHandler = handler;
		fifo_data_read = new ByteFIFO(SIZE_BUFFER);

		if ((mAdapter != null) && mAdapter.isEnabled()) {
			Log.i(TAG, "Bluetooth adapter found and enabled on device. ");
		} else {
			Log.e(TAG, "Bluetooth adapter NOT FOUND or NOT ENABLED!");
			return;
		}

		listenerList = new EventListenerList();
		devices = new HashSet<BluetoothDevice>();

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
						
					case INCOMMING_DATA:
						((BluetoothEventListener) listeners[i + 1]).incomingDataEvent(evt);
						break;
						
					case DISCONNECTED:
						((BluetoothEventListener) listeners[i + 1]).disconnected();
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

	public byte[] read() {
		return fifo_data_read.removeAll();
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
     * Set the current state of the chat connection
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
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        Rfcomm.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        Rfcomm.this.start();
    }
	
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                        MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (Rfcomm.this) {
                        switch (mState) {
                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(socket, socket.getRemoteDevice(),
                                    mSocketType);
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }
                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
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
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (Rfcomm.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    
					if (bytes > 0) {
						if (fifo_data_read.free() < bytes) {
							ByteFIFO tmp = new ByteFIFO(fifo_data_read.getCapacity() + SIZE_BUFFER);
							try {
								tmp.add(fifo_data_read.removeAll());
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							fifo_data_read = tmp;
							
						} else {
							try {
								fifo_data_read.add(buffer);
							} catch (InterruptedException e) {
								Log.e(TAG, "Failure in adding buffer to ByteFIFO", e);
								break;
							}
						}
						fireSerialAndroidEvent(new BluetoothEvent(ctx, TypeEvent.INCOMMING_DATA));
					}
                    
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    Rfcomm.this.start();
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
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
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
}
