package eu.powet.android.rfcomm;

import android.bluetooth.BluetoothDevice;

public interface BluetoothEventListener extends java.util.EventListener {
	
	
	void incomingDataEvent(BluetoothEvent evt);

	void discoveryFinished(BluetoothEvent evt);

	void disconnected();

	void newDeviceFound(BluetoothDevice device);
	
	void discoverable();

	void connected(BluetoothDevice device);
}
