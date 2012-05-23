package eu.powet.android.rfcomm;


public interface BluetoothServerListener {

	/**
	 * Called when a new remote device is connected
	 * @param connThread the ConnectionThread that handle the connection
	 */
	void newDeviceConnected(ConnectedThread connThread);
	
	/**
	 * Called when the bluetooth server is closed
	 */
	void closed();
}
