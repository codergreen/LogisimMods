/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package cake.ram2; 
 
import com.cburch.logisim.std.memory.*;

import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;

import com.cburch.logisim.data.AbstractAttributeSet;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.Value;

class RamAttributes extends RomAttributes {

	AttributeOption ao = Ram.BUS_RWMODE;
	AttributeOption per = Ram.BEGONE;
	public static List<Attribute<?>> ATTRIBUTES2 = Arrays.asList(new Attribute<?>[] {
			Mem.ADDR_ATTR, Mem.DATA_ATTR, Ram.ATTR_BUS, Ram.ATTR_PERSIST, Ram.CONTENTS_ATTR
		});
		
	RamAttributes() {
		contents = MemContents.create(addrBits.getWidth(), dataBits.getWidth());
	}
	
	void setProject(Project proj) {
		register(contents, proj);
	}
	
	@Override
	protected void copyInto(AbstractAttributeSet dest) {
		RamAttributes d = (RamAttributes) dest;
		d.addrBits = addrBits;
		d.dataBits = dataBits;
		d.ao = ao;
		d.per = per;
		d.contents = contents.clone();
	}
	
	@Override
	public List<Attribute<?>> getAttributes() {
		return ATTRIBUTES2;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <V> V getValue(Attribute<V> attr) {
		if (attr == Mem.ADDR_ATTR) return (V) addrBits;
		if (attr == Mem.DATA_ATTR) return (V) dataBits;
		if (attr == Ram.ATTR_BUS) return (V) ao;
		if (attr == Ram.ATTR_PERSIST) return (V) per;
		if (attr == Ram.CONTENTS_ATTR) return (V) contents;
		//if (attr == Ram.CONTENTS_ATTR){
		//	return (V) MemContents.create(addrBits.getWidth(),dataBits.getWidth());
		//}
		return null;
	}
	
	@Override
	public <V> void setValue(Attribute<V> attr, V value) {
		if (attr == Mem.ADDR_ATTR) {
			addrBits = (BitWidth) value;
			contents.setDimensions(addrBits.getWidth(), dataBits.getWidth());
		} else if (attr == Mem.DATA_ATTR) {
			dataBits = (BitWidth) value;
			contents.setDimensions(addrBits.getWidth(), dataBits.getWidth());
		} else if (attr == Ram.ATTR_BUS) {
			ao = (AttributeOption) value;
		} else if (attr == Ram.ATTR_PERSIST) {
			per = (AttributeOption) value;
		} else if (attr == Ram.CONTENTS_ATTR && per == Ram.PERSIST) {
			contents = (MemContents) value;
		}
		fireAttributeValueChanged(attr, value);
	}
}
