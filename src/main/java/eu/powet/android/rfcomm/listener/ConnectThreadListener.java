package eu.powet.android.rfcomm.listener;

import eu.powet.android.rfcomm.thread.ConnectedThread;
import android.bluetooth.BluetoothDevice;

public interface ConnectThreadListener {

	/**
	 * Called when the associated ConnectThread was unable
	 * to connect to the remote device
	 * @param device the remote device concerned
	 */
	void connectionFailed(BluetoothDevice device);
}
