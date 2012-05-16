package eu.powet.android.rfcomm;

import java.util.EventObject;

public class BluetoothEvent extends EventObject {
	private static final long serialVersionUID = 1L;

	private TypeEvent type;

	public BluetoothEvent(Object source, TypeEvent _type) {
		super(source);
		this.type = _type;
	}

	public TypeEvent getTypeEvent() {
		return type;
	}
}
