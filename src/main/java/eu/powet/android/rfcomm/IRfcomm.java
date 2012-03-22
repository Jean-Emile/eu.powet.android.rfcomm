package eu.powet.android.rfcomm;

import java.util.ArrayList;
import java.util.Set;

import android.bluetooth.BluetoothDevice;

/**
 * Created by jed
 * User: jedartois@gmail.com
 * Date: 22/03/12
 * Time: 12:34
 */
public interface IRfcomm {
	public void open(String address);
	public byte[] read();
	public void write(byte[] data);
	public void close();
	public boolean isconnected();
	public Set<BluetoothDevice> getDevices();
	public void discovering();
	public void addEventListener (BluetoothEventListener listener);
	public void removeEventListener (BluetoothEventListener listener);
}
