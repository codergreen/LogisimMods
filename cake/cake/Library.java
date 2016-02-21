package cake.cake;

import java.io.IOException;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.util.ZipClassLoader;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import java.util.LinkedList;
import java.util.List;

// Referenced classes of package immibis.buzzer:
//            BuzzerFactory, ComplexBuzzerFactory

public class Library extends com.cburch.logisim.tools.Library
{

    private static LinkedList tools;

    public Library(){
		tools = new LinkedList();
		ZipClassLoader loader = new ZipClassLoader("ram2.jar");
		Class<?> retClass;
		try {
			retClass = loader.loadClass("cake.ram2.Ram");
			tools.add(new AddTool((InstanceFactory)retClass.newInstance()));
		} catch (Exception e) {
			//StringUtil.format(Strings.get("jarClassNotFoundError"), className);
			System.out.println("Failed to load class");
		}
		loader = new ZipClassLoader("matrix2.jar");
		try {
			retClass = loader.loadClass("cake.matrix2.DotMatrix2");
			tools.add(new AddTool((InstanceFactory)retClass.newInstance()));
		} catch (Exception e) {
			//StringUtil.format(Strings.get("jarClassNotFoundError"), className);
			System.out.println("Failed to load class");
		}
    }

    public List getTools()
    {
        return tools;
    }

    public String getDisplayName()
    {
        return "Cake";
    }
	
	public String getName() {
		return "Cake";
	}
}