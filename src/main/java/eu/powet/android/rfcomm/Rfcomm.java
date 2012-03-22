package eu.powet.android.rfcomm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
/**
 * Created by jed
 * User: jedartois@gmail.com
 * Date: 22/03/12
 * Time: 12:36
 */
public class Rfcomm implements IRfcomm {       

	private String address=""; 
	private String TAG="Rfcomm";
	private BluetoothAdapter localAdapter=null;
	private BluetoothDevice remoteDevice=null;
	private BluetoothSocket socket=null;
	private OutputStream outStream = null;
	private InputStream inStream=null;
	private Set<BluetoothDevice> devices=null;
	private EventListenerList listenerList=null;
	private Context ctx=null;
	private final int SIZE_BUFFER =1024;
	private ByteFIFO fifo_data_read;


	public Rfcomm(Context _ctx) 
	{
		this.ctx = _ctx;
		fifo_data_read= new ByteFIFO(SIZE_BUFFER);
		localAdapter = BluetoothAdapter.getDefaultAdapter();
		if ((localAdapter!=null) && localAdapter.isEnabled()) {
			Log.i(TAG, "Bluetooth adapter found and enabled on phone. ");
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

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = localAdapter.getBondedDevices();

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

	void fireSerialAndroidEvent(BluetoothEvent evt) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = 0; i < listeners.length; i += 2)
		{

			if (listeners[i] == BluetoothEventListener.class)
			{
				switch(evt.getTypeEvent()){
				case ACTION_DISCOVERY_FINISHED:
					((BluetoothEventListener) listeners[i + 1]).discoveryFinished(evt);
					break;
				case INCOMMING_DATA:
					((BluetoothEventListener) listeners[i + 1]).incomingDataEvent(evt);
					break;
				case DISCONNECTED:
					((BluetoothEventListener) listeners[i + 1]).disconnected();
					break;
				}

			}
		}
	}

	public void open(String address)
	{
		Log.i(TAG, "Bluetooth connecting to "+address+"...");
		try     {
			remoteDevice = localAdapter.getRemoteDevice(address); 

		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Failed to get remote device with MAC address."
					+"Wrong format? MAC address must be upper case. ", 
					e);
			return;
		}

		Log.i(TAG, "Creating RFCOMM socket..."); 
		try {
			Method m = remoteDevice.getClass().getMethod("createRfcommSocket",new Class[] { int.class });
			socket = (BluetoothSocket) m.invoke(remoteDevice, 1);
			Log.i(TAG, "RFCOMM socket created.");
		} catch (NoSuchMethodException e) {
			Log.i(TAG, "Could not invoke createRfcommSocket.");
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			Log.i(TAG, "Bad argument with createRfcommSocket.");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			Log.i(TAG, "Illegal access with createRfcommSocket.");
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			Log.i(TAG, "Invocation target exception with createRfcommSocket.");
			e.printStackTrace();
		}
		Log.i(TAG, "Got socket for device "+socket.getRemoteDevice()); 
		localAdapter.cancelDiscovery();

		Log.i(TAG, "Connecting socket...");
		try {
			socket.connect(); 
			Log.i(TAG, "Socket connected.");
		} catch (IOException e) {
			try {
				Log.e(TAG, "Failed to connect socket. ", e);
				socket.close();
				Log.e(TAG, "Socket closed because of an error. ", e);
			} catch (IOException eb) {
				Log.e(TAG, "Also failed to close socket. ", eb);
			}
			return;
		}

		try {
			outStream = socket.getOutputStream(); 
			Log.i(TAG, "Output stream open.");
			inStream = socket.getInputStream();
			Log.i(TAG, "Input stream open.");
		} catch (IOException e) {
			Log.e(TAG, "Failed to create output stream.", e);  
		}

		new Thread(mLoop).start();

		return;
	}

	public void write(byte[] msg)
	{
		try {
			outStream.write(msg);
		} catch (IOException e) {
			Log.e(TAG, "Write failed.", e); 
		}

	}

	public boolean isconnected() 
	{
		return ( (inStream!=null) && (outStream!=null) );
	}

	public byte[] read()
	{
		return fifo_data_read.removeAll();
	}

	public void close()
	{
		Log.i(TAG, "Bluetooth closing... ");
		try    {
			socket.close();
			Log.i(TAG, "BT closed");
		} catch (IOException e2) {
			Log.e(TAG, "Failed to close socket. ", e2); 
		}
	}
	
	public void discovering(){

		if (localAdapter.isDiscovering()) {
			localAdapter.cancelDiscovery();
		}
		// Request discover from BluetoothAdapter
		localAdapter.startDiscovery();
	}


	private Runnable mLoop = new Runnable() {
		@Override
		public void run() {
			for(;;)
			{
				if(isconnected())
				{
					int bytesRead=0;
					byte[] inBuffer =null;
					try 
					{
						if (0<inStream.available()) {
							inBuffer = new byte[1024];
							bytesRead = inStream.read(inBuffer);
						}
					} catch (IOException e) {
						Log.e(TAG, "Read failed", e); 
					}

					if(bytesRead > 0)
					{
						if(fifo_data_read.free() < bytesRead)
						{
							ByteFIFO tmp = new ByteFIFO(fifo_data_read.getCapacity()+ SIZE_BUFFER);
							try
							{
								tmp.add(fifo_data_read.removeAll());
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							fifo_data_read = tmp;
						}
						fireSerialAndroidEvent(new BluetoothEvent(ctx,TypeEvent.INCOMMING_DATA));
					}
				}
				else
				{
					close();
					fireSerialAndroidEvent(new BluetoothEvent(ctx,TypeEvent.DISCONNECTED));
				}

			}

		}
	};

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// If it's already paired, skip it, because it's been listed already
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					devices.add(device);
				}
				// When discovery is finished, change the Activity title
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				fireSerialAndroidEvent(new BluetoothEvent(ctx,TypeEvent.ACTION_DISCOVERY_FINISHED));
			}

			if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
			{
				fireSerialAndroidEvent(new BluetoothEvent(ctx,TypeEvent.DISCONNECTED));
			}
		}
	};

	@Override
	public Set<BluetoothDevice> getDevices() {
		return devices;
	}
}