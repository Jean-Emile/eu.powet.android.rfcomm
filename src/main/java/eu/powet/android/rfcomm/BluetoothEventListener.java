package eu.powet.android.rfcomm;

public interface BluetoothEventListener extends java.util.EventListener
{
	void incomingDataEvent(BluetoothEvent evt);
	void discoveryFinished(BluetoothEvent evt);
	void disconnected();
}

