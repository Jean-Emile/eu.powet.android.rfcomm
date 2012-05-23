package eu.powet.android.rfcomm;

import android.bluetooth.BluetoothDevice;

public interface BluetoothEventListener extends java.util.EventListener {
	
	
	void discoveryFinished(BluetoothEvent evt);

	void disconnected(BluetoothDevice device);

	void newDeviceFound(BluetoothDevice device);
	
	void discoverable();

	void connected(BluetoothDevice device);
}
