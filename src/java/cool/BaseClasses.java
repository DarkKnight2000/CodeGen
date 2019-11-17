package cool;

import java.io.PrintWriter;
import java.util.*;
public class BaseClasses{
    AST.class_ Object, IO, String, Int, Bool;
	static HashMap<String,String> typeMap = new HashMap<String,String>();
    ClassPlus ObjectPlus;
	public BaseClasses(){
        Object = new AST.class_("Object", "", "", new ArrayList<AST.feature>(Arrays.asList(
			(AST.feature) new AST.method("abort", new ArrayList<AST.formal>(), "Object",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("type_name", new ArrayList<AST.formal>(), "String",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("copy", new ArrayList<AST.formal>(), "SELF_TYPE",(AST.expression) new AST.no_expr(0), 0)
		)), 0);

		IO = new AST.class_("IO", "", "Object", new ArrayList<AST.feature>(Arrays.asList(
			new AST.method("out_string", new ArrayList<AST.formal>(Arrays.asList(new AST.formal("x", "String", 0))), "SELF_TYPE",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("out_int", new ArrayList<AST.formal>(Arrays.asList(new AST.formal("x", "Int", 0))), "SELF_TYPE",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("in_string", new ArrayList<AST.formal>(), "String",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("in_int", new ArrayList<AST.formal>(), "Int",(AST.expression) new AST.no_expr(0), 0)
		)), 0);

		Int = new AST.class_("Int", "", "Object", new ArrayList<AST.feature>(), 0);
		Bool = new AST.class_("Bool", "", "Object", new ArrayList<AST.feature>(), 0);
		String = new AST.class_("String", "", "Object", new ArrayList<AST.feature>(Arrays.asList(
			(AST.feature) new AST.method("length", new ArrayList<AST.formal>(), "Int",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("concat", new ArrayList<AST.formal>(Arrays.asList(new AST.formal("s", "String", 0))), "String",(AST.expression) new AST.no_expr(0), 0),
			(AST.feature) new AST.method("substr", new ArrayList<AST.formal>(Arrays.asList(new AST.formal("i", "Int", 0), new AST.formal("l", "Int", 0))), "String",(AST.expression) new AST.no_expr(0), 0)
        )),0);
        HashMap<String, AST.method>  omap = new HashMap<String, AST.method>();
        for(AST.feature f: Object.features){
            AST.method m = (AST.method) f;
            omap.put(m.name, m);
        }
		ObjectPlus = new ClassPlus("Object", "", new HashMap<String, AST.attr>(), omap);
		typeMap.put("Int","i32");
		typeMap.put("Bool", "i1");
		typeMap.put("String", "i8*");
	}
	static String getType(String s){
		return typeMap.containsKey(s) ? typeMap.get(s) : "%class."+s;
	}
	static String getType(String s, String val){
		return "[" + (val.length()+1) + " x i8]*";
	}
	public static String writeMethod(String cname, String mname, PrintWriter out, Codegen.IntPointer g){
		String buffer = "";
		if(mname.equals("abort")){
			out.print("\ndefine void @abort_" + cname + "(" + getType(cname)+"* %this, " + "){\nentry:");
			out.println("unreachable\n}");
		}
		else if(mname.equals("type_name")){
			out.print("\ndefine i8* @type_name_" + cname + "(" + getType(cname)+"* %this, " + "){\nentry:");
			buffer += "@str."+(g.value++)+" = private unnamed_addr constant ["+(cname.length()+1)+" x i8] c\"" + cname + "\\00\", align 1\n";
			out.println("ret i8* getelementptr inbounds ([" + (cname.length()+1) + " x i8], [" + (cname.length()+1) + " x i8]* @str." + (g.value-1) + ", i32 0, i32 0)" + "\n}");
		}
		else if(mname.equals("copy")){
			out.print("\ndefine " + getType(cname) + "* @copy_" + cname + "(" + getType(cname)+"* %this, " + "){\nentry:");
			buffer += "@str."+(g.value++)+" = private unnamed_addr constant ["+(cname.length()+1)+" x i8] c\"" + cname + "\\00\", align 1\n";
			out.println("ret " + getType(cname) + " * %this\n}");
		}
		return buffer;
	}
}
