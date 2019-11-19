package cool;

import java.util.*;
// A class to contain definitions of pre-defined types in cool
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
}
