/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package cake.ram2;

import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

import javax.swing.JLabel;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.proj.Project;


import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.StdAttr;
import java.awt.Color;

public class Ram2 extends Mem {
	public static final Attribute<MemContents> CONTENTS_ATTR = new ContentsAttribute();
	
	// The following is so that instance's MemListeners aren't freed by the
	// garbage collector until the instance itself is ready to be freed.
	private WeakHashMap<Instance,MemListener> memListeners;
	
	ClockState clockState = new ClockState();
	

	/*private static Attribute<?>[] ATTRIBUTES = {
		Mem.ADDR_ATTR, Mem.DATA_ATTR, Ram.ATTR_BUS, CONTENTS_ATTR
	};
	private static Object[] DEFAULTS = {
		BitWidth.create(8), BitWidth.create(8), BUS_COMBINED, MemContents.create(8,8)
	};*/
	
	private static final int OE  = MEM_INPUTS + 0;
	private static final int CLR = MEM_INPUTS + 1;
	private static final int CLK = MEM_INPUTS + 2;
	private static final int WE  = MEM_INPUTS + 3;
	private static final int DIN = MEM_INPUTS + 4;
	
	private static Object[][] logOptions = new Object[9][];
	
	public Ram2() {
		super("Ram2", Strings.getter("ramComponent"), 3);
		setIconName("ram.gif");
		memListeners = new WeakHashMap<Instance,MemListener>();
		
		//setInstanceLogger(Logger.class);
	}

	@Override
	void configurePorts(Instance instance) {
		//Port[] ps = new Port[MEM_INPUTS];
		Object bus = instance.getAttributeValue(Ram.ATTR_BUS);
		if (bus == null) bus = Ram.BUS_COMBINED;
		boolean asynch = bus == null ? false : bus.equals(Ram.BUS_ASYNCH);
		boolean separate = bus == null ? false : bus.equals(Ram.BUS_SEPARATE);

		int portCount = MEM_INPUTS;
		if (asynch) portCount += 2;
		else if (separate) portCount += 5;
		else portCount += 3;
		Port[] ps = new Port[portCount];

		configureStandardPorts(instance, ps);
		ps[OE]  = new Port(-50, 40, Port.INPUT, 1);
		ps[OE].setToolTip(Strings.getter("ramOETip"));
		ps[CLR] = new Port(-30, 40, Port.INPUT, 1);
		ps[CLR].setToolTip(Strings.getter("ramClrTip"));
		if (!asynch) {
			ps[CLK] = new Port(-70, 40, Port.INPUT, 1);
			ps[CLK].setToolTip(Strings.getter("ramClkTip"));
		}
		if (separate) {
			ps[WE] = new Port(-110, 40, Port.INPUT, 1);
			ps[WE].setToolTip(Strings.getter("ramWETip"));
			ps[DIN] = new Port(-140, 20, Port.INPUT, DATA_ATTR);
			ps[DIN].setToolTip(Strings.getter("ramInTip"));
		} else {
			ps[DATA].setToolTip(Strings.getter("ramBusTip"));
		}
		instance.setPorts(ps);
	}
	
	@Override
	public void paintInstance(InstancePainter painter) {
		super.paintInstance(painter);
		Object busVal = painter.getAttributeValue(Ram.ATTR_BUS);
		boolean asynch = busVal == null ? false : busVal.equals(Ram.BUS_ASYNCH);
		boolean separate = busVal == null ? false : busVal.equals(Ram.BUS_SEPARATE);
		
		if (!asynch) painter.drawClock(CLK, Direction.NORTH);
		painter.drawPort(OE, Strings.get("ramOELabel"), Direction.SOUTH);
		painter.drawPort(CLR, Strings.get("ramClrLabel"), Direction.SOUTH);

		if (separate) {
			painter.drawPort(WE, Strings.get("ramWELabel"), Direction.SOUTH);
			painter.getGraphics().setColor(Color.BLACK);
			painter.drawPort(DIN, Strings.get("ramDataLabel"), Direction.EAST);
		}
	}
	
	@Override
	public AttributeSet createAttributeSet() {
		return new RamAttributes();
	}

	@Override
	MemState getState(Instance instance, CircuitState state) {
		
		BitWidth addrBits = instance.getAttributeValue(ADDR_ATTR);
		BitWidth dataBits = instance.getAttributeValue(DATA_ATTR);
		
		MemState ret = (MemState) instance.getData(state);
		if (ret == null) {
			MemContents contents = getMemContents(instance);
			ret = new MemState(contents);
			instance.setData(state, ret);
		}
		return ret;
	}
	
	@Override
	MemState getState(InstanceState state) {
		BitWidth addrBits = state.getAttributeValue(ADDR_ATTR);
		BitWidth dataBits = state.getAttributeValue(DATA_ATTR);
		
		MemState ret = (MemState) state.getData();
		if (ret == null) {
			MemContents contents = getMemContents(state.getInstance());
			ret = new MemState(contents);
			state.setData(ret);
		}
		return ret;
	}
 
	@Override
	HexFrame getHexFrame(Project proj, Instance instance, CircuitState state) {
		return RamAttributes.getHexFrame(getMemContents(instance), proj);
	}
	
	// TODO - maybe delete this method?
	MemContents getMemContents(Instance instance) {
		return instance.getAttributeValue(CONTENTS_ATTR);
	}

	@Override
	public void propagate(InstanceState state) {
		MemState myState = getState(state);
		BitWidth dataBits = state.getAttributeValue(DATA_ATTR);

		Object busVal = state.getAttributeValue(Ram.ATTR_BUS);
		boolean asynch = busVal == null ? false : busVal.equals(Ram.BUS_ASYNCH);
		boolean separate = busVal == null ? false : busVal.equals(Ram.BUS_SEPARATE);
		
		Value addrValue = state.getPort(ADDR);
		boolean chipSelect = state.getPort(CS) != Value.FALSE;
		
		boolean triggered = asynch || clockState.updateClock(state.getPort(CLK), StdAttr.TRIG_RISING);
		boolean outputEnabled = state.getPort(OE) != Value.FALSE;
		boolean shouldClear = state.getPort(CLR) == Value.TRUE;
		
		if (!chipSelect) {
			myState.setCurrent(-1);
			state.setPort(DATA, Value.createUnknown(dataBits), DELAY);
			return;
		}

		int addr = addrValue.toIntValue();
		if (!addrValue.isFullyDefined() || addr < 0)
			return;
		if (addr != myState.getCurrent()) {
			myState.setCurrent(addr);
			myState.scrollToShow(addr);
		}

		//int val = myState.getContents().get(addr);
		//state.setPort(DATA, Value.createKnown(dataBits, val), DELAY);
		
		if (!shouldClear && triggered) {
			boolean shouldStore;
			if (separate) {
				shouldStore = state.getPort(WE) != Value.FALSE;
			} else {
				shouldStore = !outputEnabled;
			}
			if (shouldStore) {
				Value dataValue = state.getPort(separate ? DIN : DATA);
				myState.getContents().set(addr, dataValue.toIntValue());
			}
		}

		if (outputEnabled) {
			int val = myState.getContents().get(addr);
			state.setPort(DATA, Value.createKnown(dataBits, val), DELAY);
		} else {
			state.setPort(DATA, Value.createUnknown(dataBits), DELAY);
		}
	}
	
	@Override
	protected void configureNewInstance(Instance instance) {
		super.configureNewInstance(instance);
		instance.addAttributeListener();
		MemContents contents = getMemContents(instance);
		MemListener listener = new MemListener(instance);
		memListeners.put(instance, listener);
		contents.addHexModelListener(listener);
	}
	
	
	private static class ContentsAttribute extends Attribute<MemContents> {
		public ContentsAttribute() {
			super("contents", Strings.getter("romContentsAttr"));
		}

		@Override
		public java.awt.Component getCellEditor(Window source, MemContents value) {
			if (source instanceof Frame) {
				Project proj = ((Frame) source).getProject();
				RamAttributes.register(value, proj);
			}
			ContentsCell ret = new ContentsCell(source, value);
			ret.mouseClicked(null);
			return ret;
		}

		@Override
		public String toDisplayString(MemContents value) {
			return Strings.get("romContentsValue");
		}

		@Override
		public String toStandardString(MemContents state) {
			int addr = state.getLogLength();
			int data = state.getWidth();
			StringWriter ret = new StringWriter();
			ret.write("addr/data: " + addr + " " + data + "\n");
			try {
				HexFile.save(ret, state);
			} catch (IOException e) { }
			return ret.toString();
		}

		@Override
		public MemContents parse(String value) {
			int lineBreak = value.indexOf('\n');
			String first = lineBreak < 0 ? value : value.substring(0, lineBreak);
			String rest = lineBreak < 0 ? "" : value.substring(lineBreak + 1);
			StringTokenizer toks = new StringTokenizer(first);
			try {
				String header = toks.nextToken();
				if (!header.equals("addr/data:")) return null;
				int addr = Integer.parseInt(toks.nextToken());
				int data = Integer.parseInt(toks.nextToken());
				MemContents ret = MemContents.create(addr, data);
				HexFile.open(ret, new StringReader(rest));
				return ret;
			} catch (IOException e) {
				return null;
			} catch (NumberFormatException e) {
				return null;
			} catch (NoSuchElementException e) {
				return null;
			}
		}
	}
		
	private static class ContentsCell extends JLabel
			implements MouseListener {
		Window source;
		MemContents contents;
		
		ContentsCell(Window source, MemContents contents) {
			super(Strings.get("romContentsValue"));
			this.source = source;
			this.contents = contents;
			addMouseListener(this);
		}

		public void mouseClicked(MouseEvent e) {
			if (contents == null) return;
			Project proj = source instanceof Frame ? ((Frame) source).getProject() : null;
			HexFrame frame = RamAttributes.getHexFrame(contents, proj);
			frame.setVisible(true);
			frame.toFront();
		}

		public void mousePressed(MouseEvent e) { }

		public void mouseReleased(MouseEvent e) { }

		public void mouseEntered(MouseEvent e) { }

		public void mouseExited(MouseEvent e) { }
	}
	
	public static class Logger extends InstanceLogger {
		@Override
		public Object[] getLogOptions(InstanceState state) {
			int addrBits = state.getAttributeValue(ADDR_ATTR).getWidth();
			if (addrBits >= logOptions.length) addrBits = logOptions.length - 1;
			synchronized(logOptions) {
				Object[] ret = logOptions[addrBits];
				if (ret == null) {
					ret = new Object[1 << addrBits];
					logOptions[addrBits] = ret;
					for (int i = 0; i < ret.length; i++) {
						ret[i] = Integer.valueOf(i);
					}
				}
				return ret;
			}
		}

		@Override
		public String getLogName(InstanceState state, Object option) {
			if (option instanceof Integer) {
				String disp = Strings.get("ramComponent");
				Location loc = state.getInstance().getLocation();
				return disp + loc + "[" + option + "]";
			} else {
				return null;
			}
		}

		@Override
		public Value getLogValue(InstanceState state, Object option) {
			if (option instanceof Integer) {
				MemState s = (MemState) state.getData();
				int addr = ((Integer) option).intValue();
				return Value.createKnown(BitWidth.create(s.getDataBits()),
						s.getContents().get(addr));
			} else {
				return Value.NIL;
			}
		}
	}
}
