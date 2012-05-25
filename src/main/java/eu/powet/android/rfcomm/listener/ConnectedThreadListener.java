package eu.powet.android.rfcomm.listener;

import eu.powet.android.rfcomm.thread.ConnectedThread;
import android.bluetooth.BluetoothDevice;

public interface ConnectedThreadListener {

	/**
	 * Called when a new remote device is connected
	 * @param connThread the ConnectionThread that handle the connection
	 */	
	void newDeviceConnected(ConnectedThread ct);
	
	/**
	 * Called when the connection with the remote device
	 * has been lost
	 * @param device the concerned remote device
	 */
	void connectionLost(BluetoothDevice device);
	
	/**
	 * Called when the ConnectedThread reads data
	 * from the remote device
	 * @param device the remote device who sent the data
	 * @param bytes data length in the buffer
	 * @param buffer
	 */
	void incomingData(BluetoothDevice device, int bytes, byte[] buffer);
	
	/**
	 * Called when the writing to the remote device was done
	 * @param device the remote device who received the data
	 * @param data
	 */
	void sentData(BluetoothDevice device, byte[] data);
}
