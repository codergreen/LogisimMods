/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package cake.matrix2;
 
//import com.cburch.logisim.std.io.Io;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Arrays;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.std.wiring.DurationAttribute;
import com.cburch.logisim.util.GraphicsUtil;

import com.cburch.logisim.util.StringGetter;

public class DotMatrix2 extends InstanceFactory {

	static final AttributeOption SHAPE_CIRCLE
		= new AttributeOption("circle", Strings.getter("ioShapeCircle"));
	static final AttributeOption SHAPE_SQUARE
		= new AttributeOption("square", Strings.getter("ioShapeSquare"));
	static final AttributeOption ADJUST_AUTO
		= new AttributeOption("auto", Strings.getter2("Auto adjust"));
	static final AttributeOption ADJUST_NONE
		= new AttributeOption("none", Strings.getter2("Don't adjust"));
	
	static final Attribute<Integer> ATTR_MATRIX_COLS
		= Attributes.forIntegerRange("matrixcols",
				Strings.getter("ioMatrixCols"), 1, 256);
	static final Attribute<Integer> ATTR_MATRIX_ROWS
		= Attributes.forIntegerRange("matrixrows",
				Strings.getter("ioMatrixRows"), 1, 256);
	static final Attribute<AttributeOption> ATTR_DOT_SHAPE
		= Attributes.forOption("dotshape", Strings.getter("ioMatrixShape"),
			new AttributeOption[] { SHAPE_CIRCLE, SHAPE_SQUARE });
	static final Attribute<AttributeOption> ATTR_ADJUST
		= Attributes.forOption("adjust", Strings.getter2("Should rows pin auto adjust pin size?"),
			new AttributeOption[] { ADJUST_AUTO, ADJUST_NONE });
	static final Attribute<Integer> ATTR_PERSIST = new DurationAttribute("persist",
			Strings.getter("ioMatrixPersistenceAttr"), 0, Integer.MAX_VALUE);

	boolean triggered = false;		
	
	public DotMatrix2() {
		super("DotMatrix2", Strings.getter2("Dot Matrix 2"));
		setAttributes(new Attribute<?>[] {
				ATTR_MATRIX_COLS, ATTR_MATRIX_ROWS,
				Io.ATTR_OFF_COLOR, ATTR_PERSIST, ATTR_DOT_SHAPE, ATTR_ADJUST
			}, new Object[] {
				Integer.valueOf(64), Integer.valueOf(64),
				Color.DARK_GRAY, Integer.valueOf(0), SHAPE_SQUARE, ADJUST_AUTO
			});
		setIconName("dotmat.gif");
	}

	@Override
	public Bounds getOffsetBounds(AttributeSet attrs) {
		int cols = attrs.getValue(ATTR_MATRIX_COLS).intValue();
		int rows = attrs.getValue(ATTR_MATRIX_ROWS).intValue();
		return Bounds.create(-15, -10 * rows, 10 * cols + 10, 10 * rows);
	}

	@Override
	protected void configureNewInstance(Instance instance) {
		instance.addAttributeListener();
		updatePorts(instance);
	}

	@Override
	protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
		if (attr == ATTR_MATRIX_ROWS || attr == ATTR_MATRIX_COLS) {
			instance.recomputeBounds();
			updatePorts(instance);
		}
	}
	
	private void updatePorts(Instance instance) {
		int rows = instance.getAttributeValue(ATTR_MATRIX_ROWS).intValue();
		int cols = instance.getAttributeValue(ATTR_MATRIX_COLS).intValue();
		boolean adj = instance.getAttributeValue(ATTR_ADJUST) == ADJUST_AUTO;
		Port[] ps;
		ps = new Port[cols+3];
		for (int i = 0; i < cols; i++) {
			ps[i] = new Port(10 * i, 0, Port.INPUT, 8);
			ps[i].setToolTip(new StringGetter2("Column Pin"));
		}
		if(adj){
			if(rows<=2)  ps[cols] = new Port(-10, -10, Port.INPUT, 1);
			else if(rows<=4)  ps[cols] = new Port(-10, -10, Port.INPUT, 2);
			else if(rows<=8)  ps[cols] = new Port(-10, -10, Port.INPUT, 3);
			else if(rows<=16)  ps[cols] = new Port(-10, -10, Port.INPUT, 4);
			else if(rows<=32)  ps[cols] = new Port(-10, -10, Port.INPUT, 5);
			else if(rows<=64)  ps[cols] = new Port(-10, -10, Port.INPUT, 6);
			else if(rows<=128)  ps[cols] = new Port(-10, -10, Port.INPUT, 7);
			else ps[cols] = new Port(-10, -10, Port.INPUT, 8);
		}
		else{
			ps[cols] = new Port(-10, -10, Port.INPUT, 8);
		}
		ps[cols].setToolTip(new StringGetter2("Row Selector"));
		ps[cols+1] = new Port(-10, -20, Port.INPUT, 1);
		ps[cols+1].setToolTip(new StringGetter2("Update Whole Screen"));
		ps[cols+2] = new Port(-10, -30, Port.INPUT, 1);
		ps[cols+2].setToolTip(new StringGetter2("Update Changed Rows"));
		instance.setPorts(ps);
		setPorts(ps);
	}
	
	private State getState(InstanceState state) {
		int rows = state.getAttributeValue(ATTR_MATRIX_ROWS).intValue();
		int cols = state.getAttributeValue(ATTR_MATRIX_COLS).intValue();
		long clock = state.getTickCount();
		
		State data = (State) state.getData();
		if(state.getPort(cols+1)==Value.TRUE && !triggered){
			data.UpdateDisplay();
			triggered=true;
			//int length=cols*rows;
			//for(int i=0;i<length;++i){
				//Arrays.fill(data.grid[i], Value.UNKNOWN);
				//Arrays.fill(data.persistTo[i], clock - 1);
			//}
		}
		else if(state.getPort(cols+2)==Value.TRUE && !triggered){
			data.PartialUpdateDisplay();
			triggered=true;
		}
		if((state.getPort(cols+1)==Value.FALSE || state.getPort(cols+1)==Value.UNKNOWN) && (state.getPort(cols+2)==Value.FALSE || state.getPort(cols+2)==Value.UNKNOWN)){
			triggered=false;
		}
		if (data == null) {
			data = new State(rows, cols, clock);
			state.setData(data);
		} else {
			data.updateSize(rows, cols, clock);
		}
		return data;
	}

	@Override
	public void propagate(InstanceState state) {
		int rows = state.getAttributeValue(ATTR_MATRIX_ROWS).intValue();
		int cols = state.getAttributeValue(ATTR_MATRIX_COLS).intValue();
		long clock = state.getTickCount();
		long persist = clock + state.getAttributeValue(ATTR_PERSIST).intValue();
		
		State data = getState(state);
		Value rowSelect = state.getPort(cols);
		Value[] v = rowSelect.getAll();
		int i=0;
		for(int j=0;j<v.length;++j){
			if(v[j]==Value.TRUE) i+=Math.pow(2,j);
		}
		if(i>=rows) i=0;
		data.setRow(i, state, persist);
		
	}

	@Override
	public void paintInstance(InstancePainter painter) {
		Color onColor = painter.getAttributeValue(Io.ATTR_ON_COLOR);
		Color offColor = painter.getAttributeValue(Io.ATTR_OFF_COLOR);
		boolean drawSquare = painter.getAttributeValue(ATTR_DOT_SHAPE) == SHAPE_SQUARE;

		State data = getState(painter);
		long ticks = painter.getTickCount();
		Bounds bds = painter.getBounds();
		boolean showState = painter.getShowState();
		Graphics g = painter.getGraphics();
		int rows = data.rows;
		int cols = data.cols;
		//Color me = data == null ? Color.BLACK : (Color) data.getColor();
		for (int j = 0; j < rows; j++) {
			for (int i = 0; i < cols; i++) {
				int x = bds.getX() + 10 * i + 10;
				int y = bds.getY() + 10 * j;
				if (showState) {
					Value[] vals = data.getDisplayLed(j, i, ticks);
					
					int red=0;
					int green=0;
					int blue=0;
					Value red1 = vals[0];
					Value red2 = vals[1];
					Value red4 = vals[2];
					Value green1 = vals[3];
					Value green2 = vals[4];
					Value green4 = vals[5];
					Value blue1 = vals[6];
					Value blue2 = vals[7];
					if(red1 == Value.TRUE) red+=32;
					if(red2 == Value.TRUE) red+=64;
					if(red4 == Value.TRUE) red+=128;
					if(green1 == Value.TRUE) green+=32;
					if(green2 == Value.TRUE) green+=64;
					if(green4 == Value.TRUE) green+=128;
					if(blue1 == Value.TRUE) blue+=80;
					if(blue2 == Value.TRUE) blue+=144;
					
					Color c = new Color(red,green,blue);
					g.setColor(c);
					if (drawSquare) g.fillRect(x, y, 10, 10);
					else g.fillOval(x + 1, y + 1, 8, 8);
				} else {
					g.setColor(Color.GRAY);
					g.fillOval(x + 1, y + 1, 8, 8);
				}
			}
		}
		g.setColor(Color.BLACK);
		GraphicsUtil.switchToWidth(g, 2);
		g.drawRect(bds.getX()+10, bds.getY(), bds.getWidth()-10, bds.getHeight());
		GraphicsUtil.switchToWidth(g, 1);
		painter.drawPorts();
	}
	
	private static class State implements InstanceData, Cloneable {
		private int rows;
		private int cols;
		public Value[][] grid;
		public Value[][] display;
		public long[][] persistTo;
		public boolean[] rowChanged;
		
		public State(int rows, int cols, long curClock) {
			this.rows = -1;
			this.cols = -1;
			updateSize(rows, cols, curClock);
			UpdateDisplay();
		}
		
		@Override
		public Object clone() {
			try {
				State ret = (State) super.clone();
				ret.grid = this.grid.clone();
				ret.persistTo = this.persistTo.clone();
				return ret;
			} catch (CloneNotSupportedException e) {
				return null;
			}
		}
		
		public void UpdateDisplay(){
			display = grid.clone();
			grid = new Value[rows * cols][8];
			for(int i=0;i<rows;++i){
				rowChanged[i]=false;
			}
		}
		public void PartialUpdateDisplay(){
			//display = grid.clone();
			for(int i=0;i<rows;++i){
				if(rowChanged[i]){
					int gridloc = (i + 1) * cols - 1;
					int stride = -1;
					for(int k=cols-1;k>=0;--k,gridloc+=stride){
						display[gridloc] = grid[gridloc].clone();
					}
				}
				rowChanged[i]=false;
			}
			grid = new Value[rows * cols][8];
			
		}
		
		private void updateSize(int rows, int cols, long curClock) {
			if (this.rows != rows || this.cols != cols) {
				this.rows = rows;
				this.cols = cols;
				int length = rows * cols;
				grid = new Value[length][8];
				display = new Value[length][8];
				persistTo = new long[length][8];
				rowChanged = new boolean[rows];
				for(int i=0;i<rows;++i){
					rowChanged[i]=false;
				}
				for(int i=0;i<length;++i){
					Arrays.fill(display[i], Value.UNKNOWN);
					Arrays.fill(grid[i], Value.UNKNOWN);
					Arrays.fill(persistTo[i], curClock - 1);
				}
			}
		}
		
		private Value[] get(int row, int col, long curTick) {
			int index = row * cols + col;
			Value[] ret = grid[index];
			for (int j = 0; j < 8; j++) {
				if (ret[j] == Value.FALSE && persistTo[index][j] - curTick >= 0) {
					ret[j] = Value.TRUE;
				}
			}
			return ret;
		}
		
		private Value[] getDisplayLed(int row, int col, long curTick) {
			int index = row * cols + col;
			Value[] ret = display[index];
			//for (int j = 0; j < 8; j++) {
			//	if (ret[j] == Value.FALSE && persistTo[index][j] - curTick >= 0) {
			//		ret[j] = Value.TRUE;
			//	}
			//}
			return ret;
		}
		
		private void setRow(int index, InstanceState state, long persist) {
			int gridloc = (index + 1) * cols - 1;
			int stride = -1;
			rowChanged[index]=true;
			for(int k=cols-1;k>=0;--k,gridloc+=stride){
				Value rowVector = state.getPort(k);
				Value[] vals = rowVector.getAll();
				for (int j = 0; j < 8; j++) {
					Value val = vals[j];
					if (grid[gridloc][j] == Value.TRUE) {
						persistTo[gridloc][j] = persist - 1;
					}
					grid[gridloc][j] = vals[j];
					if (val == Value.TRUE) {
						persistTo[gridloc][j] = persist;
					}
				}
			}
		}
		
		private void setColumn(int index, Value colVector, long persist) {
			int gridloc = (rows - 1) * cols + index;
			int stride = -cols;
			Value[] vals = colVector.getAll();
			for (int i = 0; i < vals.length; i++, gridloc += stride) {
				for (int j = 0; j < 8; j++) {
				Value val = vals[i];
				if (grid[gridloc][j] == Value.TRUE) {
					persistTo[gridloc][j] = persist - 1;
				}
				grid[gridloc][j] = val;
				if (val == Value.TRUE) {
					persistTo[gridloc][j] = persist;
				}
				}
			}
		}
		
		private void setSelect(Value rowVector, Value colVector, long persist) {
			Value[] rowVals = rowVector.getAll();
			Value[] colVals = colVector.getAll();
			int gridloc = 0;
			for (int i = rowVals.length - 1; i >= 0; i--) {
				Value wholeRow = rowVals[i];
				if (wholeRow == Value.TRUE) {
					for (int j = colVals.length - 1; j >= 0; j--, gridloc++) {
						for (int k = 0; k < 8; k++) {
						Value val = colVals[colVals.length - 1 - j];
						if (grid[gridloc][k] == Value.TRUE) {
							persistTo[gridloc][k] = persist - 1;
						}
						grid[gridloc][k] = val;
						if (val == Value.TRUE) {
							persistTo[gridloc][k] = persist;
						}
						}
					}
				} else {
					if (wholeRow != Value.FALSE) wholeRow = Value.ERROR;
					for (int j = colVals.length - 1; j >= 0; j--, gridloc++) {
						for (int k = 0; k < 8; k++) {
						if (grid[gridloc][k] == Value.TRUE) {
							persistTo[gridloc][k] = persist - 1;
						}
						grid[gridloc][k] = wholeRow;
						}
					}
				}
			}
		}
	}
	
	
	class StringGetter2 implements StringGetter{
		public String s;
		StringGetter2(String s){
			this.s=s;
		}
		public String get()
        {
            return s;
        }
		
		@Override
		public String toString() { return get(); }
	}
}
