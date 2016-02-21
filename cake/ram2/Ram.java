/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package cake.ram2;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceLogger;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;

//Addon test libs
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
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.gui.main.Frame;

public class Ram extends Mem {
	
	// The following is so that instance's MemListeners aren't freed by the
	// garbage collector until the instance itself is ready to be freed.
	private WeakHashMap<Instance,MemListener> memListeners;
	
	
	static final AttributeOption BUS_COMBINED
		= new AttributeOption("combined", Strings.getter("ramBusSynchCombined"));
	static final AttributeOption BUS_ASYNCH
		= new AttributeOption("asynch", Strings.getter("ramBusAsynchCombined"));
	static final AttributeOption BUS_SEPARATE
		= new AttributeOption("separate", Strings.getter("ramBusSeparate"));
	static final AttributeOption BUS_RWMODE
		= new AttributeOption("rwmode", Strings.getter2("Read and Write Separated"));
		
	static final AttributeOption PERSIST
		= new AttributeOption("persist", Strings.getter2("Yes"));
	static final AttributeOption BEGONE
		= new AttributeOption("begone", Strings.getter2("No"));

	static final Attribute<AttributeOption> ATTR_BUS = Attributes.forOption("bus",
			Strings.getter("ramBusAttr"),
			new AttributeOption[] { BUS_COMBINED, BUS_ASYNCH, BUS_SEPARATE, BUS_RWMODE });
			
	static final Attribute<AttributeOption> ATTR_PERSIST = Attributes.forOption("persist",
			Strings.getter2("Persist?"),
			new AttributeOption[] { PERSIST, BEGONE });

	
	public static final Attribute<MemContents> CONTENTS_ATTR = new ContentsAttribute();
			
	private static Attribute<?>[] ATTRIBUTES = {
		Mem.ADDR_ATTR, Mem.DATA_ATTR, ATTR_BUS, ATTR_PERSIST, CONTENTS_ATTR
	};
	private static Object[] DEFAULTS = {
		BitWidth.create(8), BitWidth.create(8), BUS_RWMODE, BEGONE, MemContents.create(8,8)
	};
	
	private static final int OE  = MEM_INPUTS + 0;
	private static final int CLR = MEM_INPUTS + 1;
	private static final int CLK = MEM_INPUTS + 2;
	private static final int WE  = MEM_INPUTS + 3;
	private static final int DIN = MEM_INPUTS + 4;
	private static final int DOUT = MEM_INPUTS + 5;
	private static final int DADD = MEM_INPUTS + 6;
	private static final int SELDIS = MEM_INPUTS + 7;

	private static Object[][] logOptions = new Object[9][];

	public Ram() {
		super("RAM", Strings.getter2("RAM2"), 3);
		setIconName("ram.gif");
		memListeners = new WeakHashMap<Instance,MemListener>();
		setInstanceLogger(Logger.class);
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
	
	@Override
	protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
		super.instanceAttributeChanged(instance, attr);
		configurePorts(instance);
	}
	
	@Override
	void configurePorts(Instance instance) {
		Object bus = instance.getAttributeValue(ATTR_BUS);
		if (bus == null) bus = BUS_COMBINED;
		boolean asynch = bus == null ? false : bus.equals(BUS_ASYNCH);
		boolean separate = bus == null ? false : bus.equals(BUS_SEPARATE);
		boolean rw = bus == null ? false : bus.equals(BUS_RWMODE);

		int portCount = MEM_INPUTS;
		if (asynch) portCount += 2;
		else if (separate) portCount += 5;
		else if (rw) portCount += 8;
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
		if(rw){
			ps[WE] = new Port(-110, 40, Port.INPUT, 1);
			ps[WE].setToolTip(Strings.getter("ramWETip"));
			ps[DIN] = new Port(-140, 20, Port.INPUT, DATA_ATTR);
			ps[DIN].setToolTip(Strings.getter("ramInTip"));
			//FIXME naming
			ps[DOUT] = new Port(-10, 40, Port.INPUT, 1);
			ps[DOUT].setToolTip(Strings.getter2("Should data output?"));
			ps[SELDIS] = new Port(-130, 40, Port.INPUT, 1);
			ps[SELDIS].setToolTip(Strings.getter2("Display read address?"));
			ps[DADD] = new Port(-140, -20, Port.INPUT, ADDR_ATTR);
			ps[DADD].setToolTip(Strings.getter2("Address of data to output"));
		} else if (separate) {
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
	public AttributeSet createAttributeSet() {
		//return AttributeSets.fixedSet(ATTRIBUTES, DEFAULTS);
		return new RamAttributes();
	}

	@Override
	MemState getState(InstanceState state) {
		BitWidth addrBits = state.getAttributeValue(ADDR_ATTR);
		BitWidth dataBits = state.getAttributeValue(DATA_ATTR);
		Instance instance = state.getInstance();
		Object bus = instance.getAttributeValue(ATTR_PERSIST);
		boolean persist = bus == null ? false : bus.equals(PERSIST);

		RamState myState = (RamState) state.getData();
		if (myState == null) {
			MemContents contents = getMemContents(state.getInstance());
			myState = new RamState(instance, contents, new MemListener(instance));
			state.setData(myState);
		}
		//else if (myState == null) {
		//	MemContents contents = MemContents.create(addrBits.getWidth(), dataBits.getWidth());
		//	myState = new RamState(instance, contents, new MemListener(instance));
		//	state.setData(myState);
		//}
		//else if(persist) {
		//	return myState;
		//} else {
		//	myState.setRam(instance);
		//}
		return myState;
	}

	@Override
	MemState getState(Instance instance, CircuitState state) {
		BitWidth addrBits = instance.getAttributeValue(ADDR_ATTR);
		BitWidth dataBits = instance.getAttributeValue(DATA_ATTR);
		Object bus = instance.getAttributeValue(ATTR_PERSIST);
		boolean persist = bus == null ? false : bus.equals(PERSIST);

		RamState myState = (RamState) instance.getData(state);
		if (myState == null) {
			MemContents contents = getMemContents(instance);
			myState = new RamState(instance, contents, new MemListener(instance));
			instance.setData(state, myState);
		}
		//else if(myState == null){
		//	MemContents contents = MemContents.create(addrBits.getWidth(), dataBits.getWidth());
		//	myState = new RamState(instance, contents, new MemListener(instance));
		//	instance.setData(state, myState);
		//}
		//else if(persist) {
		//	return myState;
		//} else {
		//	myState.setRam(instance);
		//}
		return myState;
	}

	MemContents getMemContents(Instance instance) {
		return instance.getAttributeValue(CONTENTS_ATTR);
	}
	
	@Override
	HexFrame getHexFrame(Project proj, Instance instance, CircuitState circState) {
		Object bus = instance.getAttributeValue(ATTR_PERSIST);
		boolean persist = bus == null ? false : bus.equals(PERSIST);
		
		//if(persist)
			return RamAttributes.getHexFrame(getMemContents(instance), proj);
		
		//RamState state = (RamState) getState(instance, circState);
		//return state.getHexFrame(proj);
	}

	@Override
	public void propagate(InstanceState state) {
		RamState ramState = (RamState) getState(state);
		MemState myState = (MemState) getState(state);
		BitWidth dataBits = state.getAttributeValue(DATA_ATTR);
		Object busVal = state.getAttributeValue(ATTR_BUS);
		boolean asynch = busVal == null ? false : busVal.equals(BUS_ASYNCH);
		boolean separate = busVal == null ? false : busVal.equals(BUS_SEPARATE);
		boolean rw = busVal == null ? false : busVal.equals(BUS_RWMODE);

		Value addrValue = state.getPort(ADDR);
		boolean chipSelect = state.getPort(CS) != Value.FALSE;
		boolean triggered = asynch || ramState.setClock(state.getPort(CLK), StdAttr.TRIG_RISING);
		boolean outputEnabled = state.getPort(OE) != Value.FALSE;
		boolean shouldClear = state.getPort(CLR) == Value.TRUE;
		
		if (shouldClear) {
			myState.getContents().clear();
		}
		
		if (!chipSelect) {
			myState.setCurrent(-1);
			state.setPort(DATA, Value.createUnknown(dataBits), DELAY);
			return;
		}

		boolean skipJump=false;
		if(rw)
			if(state.getPort(SELDIS) == Value.TRUE)
				skipJump=true;
		
		int addr = addrValue.toIntValue();
		if (!addrValue.isFullyDefined() || addr < 0)
			return;
		if (addr != myState.getCurrent() && !skipJump) {
			myState.setCurrent(addr);
			myState.scrollToShow(addr);
		}

		if (!shouldClear && triggered) {
			boolean shouldStore;
			if (separate) {
				shouldStore = state.getPort(WE) != Value.FALSE;
			} else if(rw){
				shouldStore = state.getPort(WE) != Value.FALSE;
				outputEnabled = state.getPort(DOUT) != Value.FALSE;
				
			} else {
				shouldStore = !outputEnabled;
			}
			if (shouldStore) {
				Value dataValue;
				if(separate||rw){
					dataValue = state.getPort(DIN);
				}
				else{
					dataValue = state.getPort(DATA);
				}
				myState.getContents().set(addr, dataValue.toIntValue());
			}
		}

		if (outputEnabled) {
			int val;
			if(rw){
				Value daddValue = state.getPort(DADD);
				int dadd = daddValue.toIntValue();
				val = myState.getContents().get(dadd);
				if (dadd != myState.getCurrent() && skipJump) {
					myState.setCurrent(dadd);
					myState.scrollToShow(dadd);
				}
				
			}
			else{
				val = myState.getContents().get(addr);
			}
			state.setPort(DATA, Value.createKnown(dataBits, val), DELAY);
		} else {
			state.setPort(DATA, Value.createUnknown(dataBits), DELAY);
		}
	}

	@Override
	public void paintInstance(InstancePainter painter) {
		super.paintInstance(painter);
		Object busVal = painter.getAttributeValue(ATTR_BUS);
		boolean asynch = busVal == null ? false : busVal.equals(BUS_ASYNCH);
		boolean separate = busVal == null ? false : busVal.equals(BUS_SEPARATE);
		boolean rw = busVal == null ? false : busVal.equals(BUS_RWMODE);
		
		if (!asynch) painter.drawClock(CLK, Direction.NORTH);
		painter.drawPort(OE, Strings.get("ramOELabel"), Direction.SOUTH);
		painter.drawPort(CLR, Strings.get("ramClrLabel"), Direction.SOUTH);

		if (separate) {
			painter.drawPort(WE, Strings.get("ramWELabel"), Direction.SOUTH);
			painter.getGraphics().setColor(Color.BLACK);
			painter.drawPort(DIN, Strings.get("ramDataLabel"), Direction.EAST);
		}
		else if(rw){
			painter.drawPort(WE, Strings.get("ramWELabel"), Direction.SOUTH);
			painter.drawPort(DOUT, "rr", Direction.SOUTH);
			painter.drawPort(SELDIS, "?", Direction.SOUTH);
			painter.getGraphics().setColor(Color.BLACK);
			painter.drawPort(DIN, Strings.get("ramDataLabel"), Direction.EAST);
			painter.drawPort(DADD, "A", Direction.EAST);
			
		}
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
	
	
	private static class RamState extends MemState
			implements InstanceData, AttributeListener {
		private Instance parent;
		private MemListener listener;
		private HexFrame hexFrame = null;
		private ClockState clockState;

		RamState(Instance parent, MemContents contents, MemListener listener) {
			super(contents);
			this.parent = parent;
			this.listener = listener;
			this.clockState = new ClockState();
			if (parent != null) parent.getAttributeSet().addAttributeListener(this);
			contents.addHexModelListener(listener);
		}
		
		void setRam(Instance value) {
			if (parent == value) return;
			if (parent != null) parent.getAttributeSet().removeAttributeListener(this);
			parent = value;
			if (value != null) value.getAttributeSet().addAttributeListener(this);
		}
		
		@Override
		public RamState clone() {
			RamState ret = (RamState) super.clone();
			ret.parent = null;
			ret.clockState = this.clockState.clone();
			ret.getContents().addHexModelListener(listener);
			return ret;
		}
		
		// Retrieves a HexFrame for editing within a separate window
		public HexFrame getHexFrame(Project proj) {
			if (hexFrame == null) {
				hexFrame = new HexFrame(proj, getContents());
				hexFrame.addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosed(WindowEvent e) {
						hexFrame = null;
					}
				});
			}
			return hexFrame;
		}
		
		//
		// methods for accessing the write-enable data
		//
		public boolean setClock(Value newClock, Object trigger) {
			return clockState.updateClock(newClock, trigger);
		}

		public void attributeListChanged(AttributeEvent e) { }

		public void attributeValueChanged(AttributeEvent e) {
			AttributeSet attrs = e.getSource();
			BitWidth addrBits = attrs.getValue(Mem.ADDR_ATTR);
			BitWidth dataBits = attrs.getValue(Mem.DATA_ATTR);
			getContents().setDimensions(addrBits.getWidth(), dataBits.getWidth());
		}
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
