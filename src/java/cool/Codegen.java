package cool;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import cool.AST.expression;

public class Codegen{
	private ArrayList<String> ClsToWrite, baseClasses, preDefMthds;
	private ArrayList<AST.method> allWrittenMthds;
	private HashMap<AST.method, ClassPlus> mthdsToWrite, mthdsWriting;
	private HashMap<String,ClassPlus>classMap;
	private HashMap<String,String> typeMap;
	private HashMap<String,HashSet<String>> castToWrite;
	private HashMap<String, ArrayList<AST.class_>> inherGraph;
	private HashMap<String, ArrayList<AST.attr>> initOrder;
	private HashMap<String, HashMap<String , Integer>> attrNumMap;
	private int global = 0; 
	private String buffer = "", voidErr;
	AST.static_dispatch divErr;
	public Codegen(AST.program program, PrintWriter out){
		//Write Code generator code here
		out.println("; I am a comment in LLVM-IR. Feel free to remove me.");
		classMap = new HashMap<String,ClassPlus>();
		typeMap = new HashMap<String,String>(); // Map from typename to type in IR
		castToWrite = new HashMap<String,HashSet<String>>(); // Parent to children casting methods needed
		mthdsToWrite = new HashMap<AST.method, ClassPlus>(); // A Buffer to collect methods to be written
		mthdsWriting = new HashMap<AST.method, ClassPlus>(); // A buffer on which we can loop over to write and collect required methods at a time
		allWrittenMthds = new ArrayList<AST.method>(); // List of methods written so far. Required to avoid writing a method two times
		inherGraph = new HashMap<String, ArrayList<AST.class_>>(); // Inheritance graph 
		initOrder = new HashMap<String, ArrayList<AST.attr>>(); // For each class, it contains the order of attributes in which they have to be initialised( checks for dependence of initialisaton of attribute on other atrribute values)
		attrNumMap = new HashMap<String, HashMap<String , Integer>>(); // For each class stores the offset to get the attribute in 'getelementptr' in LLVM-IR
		typeMap.put("Int","i32"); // Contains only int, string, bool
		typeMap.put("Bool", "i1"); //  We refer to these 3 as basic types from now
		typeMap.put("String", "i8*");
		ClsToWrite = new ArrayList<String>();
		baseClasses = new ArrayList<String>(Arrays.asList("Object", "IO", "Int", "String", "Bool"));
		preDefMthds = new ArrayList<String>(Arrays.asList("abort", "type_name", "copy", "out_string", "out_int", "in_int", "in_string", "concat", "substr", "length")); // List of pre-defined methods
		divErr = new AST.static_dispatch(new AST.new_("IO", 0), "IO", "out_string", new ArrayList<expression>(Arrays.asList((AST.expression) new AST.string_const("Error: Division by 0", 0))), 0); // An expression to use for error handling of division by 0.
		divErr.type = "IO";
		divErr.caller.type = "IO";
		divErr.actuals.get(0).type = "String";
		voidErr = "Error: Static Dispatch on void";
		processProgram(program);

		out.println("source_filename = \""+program.classes.get(0).filename+"\"\n"+"target datalayout = \"e-m:e-i64:64-f80:128-n8:16:32:64-S128\"\ntarget triple = \"x86_64-pc-linux-gnu\"");
		ClassPlus main_class = classMap.get("Main");
		ClsToWrite.addAll(classMap.keySet());
		mthdsWriting.put(main_class.mlist.get("main"), main_class);
		for(ClassPlus cls: classMap.values()){
			if(!typeMap.containsKey(cls.name)) writeClass(out, cls);
		}
		for(ClassPlus cls: classMap.values()){
			if(!typeMap.containsKey(cls.name)) writeConstructor(out, cls); // Int, bool, string dont require constructors or declarations
		}
		mthdsWriting.putAll(mthdsToWrite);
		while(!mthdsWriting.isEmpty()){
			mthdsToWrite.clear();
			for(AST.method m: mthdsWriting.keySet()){
				System.out.println("wr mt - " + m.name);
				if(!allWrittenMthds.contains(m)){
					writeMethod(out, m, mthdsWriting.get(m));
					allWrittenMthds.add(m);
				}
			}
			mthdsWriting.clear();
			mthdsWriting.putAll(mthdsToWrite);
		}
		/*for(ClassPlus cls: classMap.values()){
			writeClass(out, cls);
			for(AST.method m: cls.mlist.values()) methdsToWrite.put(m,cls);
		}

		for(ClassPlus cls: classMap.values()) writeConstructor(out, cls);
		for(AST.method meth : methdsToWrite.keySet()) writeMethod(out, meth, methdsToWrite.get(meth));*/
		
		for(String s: castToWrite.keySet()){
			for(String sc: castToWrite.get(s)) writeCaster(out, classMap.get(s), classMap.get(sc));
		}
		
		// Required inbuilt functions for some pre-defined methods
		out.println("declare void @exit(i32)");
		out.println("declare i32 @scanf(i8*, ...) #1");
		out.println("declare i32 @printf(i8*, ...) #1");
		out.println("declare i8* @malloc(i32) #1");
		out.println("declare i64 @strlen(i8*) #1");
		out.println("declare i8* @strcpy(i8*, i8*) #1");
		out.println("declare i8* @strcat(i8*, i8*) #1");
		out.println("declare i8* @strncpy(i8*, i8*, i32) #1");
		out.println("" + 
		"attributes #0 = { noinline nounwind optnone uwtable \"correctly-rounded-divide-sqrt-fp-math\"=\"false\" \"disable-tail-calls\"=\"false\" \"less-precise-fpmad\"=\"false\" \"no-frame-pointer-elim\"=\"true\" \"no-frame-pointer-elim-non-leaf\" \"no-infs-fp-math\"=\"false\" \"no-jump-tables\"=\"false\" \"no-nans-fp-math\"=\"false\" \"no-signed-zeros-fp-math\"=\"false\" \"no-trapping-math\"=\"false\" \"stack-protector-buffer-size\"=\"8\" \"target-cpu\"=\"x86-64\" \"target-features\"=\"+fxsr,+mmx,+sse,+sse2,+x87\" \"unsafe-fp-math\"=\"false\" \"use-soft-float\"=\"false\" }\n" +
		"attributes #1 = { \"correctly-rounded-divide-sqrt-fp-math\"=\"false\" \"disable-tail-calls\"=\"false\" \"less-precise-fpmad\"=\"false\" \"no-frame-pointer-elim\"=\"true\" \"no-frame-pointer-elim-non-leaf\" \"no-infs-fp-math\"=\"false\" \"no-nans-fp-math\"=\"false\" \"no-signed-zeros-fp-math\"=\"false\" \"no-trapping-math\"=\"false\" \"stack-protector-buffer-size\"=\"8\" \"target-cpu\"=\"x86-64\" \"target-features\"=\"+fxsr,+mmx,+sse,+sse2,+x87\" \"unsafe-fp-math\"=\"false\" \"use-soft-float\"=\"false\" }\n" +
		
		"!llvm.module.flags = !{!0}\n" +
		"!llvm.ident = !{!1}\n" +
		
		"!0 = !{i32 1, !\"wchar_size\", i32 4}\n" +
		"!1 = !{!\"clang version 6.0.0-1ubuntu2 (tags/RELEASE_600/final)\"}\n");
	}
	/*private void checkForWrite(ClassPlus cls){
		System.out.println("checking-"+cls.name);
		for(AST.attr a: cls.alist.values()){
			if(!ClsToWrite.contains(a.typeid)) {ClsToWrite.add(a.typeid);System.out.println("x-"+a.typeid);}
			if(!(a.value instanceof AST.no_expr) && !ClsToWrite.contains(a.value.type)) {ClsToWrite.add(a.value.type);System.out.println("x-"+a.value.type);}
		}
	}
	private void checkForWrite(AST.method meth){
		for(AST.formal a: meth.formals){
			if(!ClsToWrite.contains(a.typeid)) ClsToWrite.add(a.typeid);
		}
		if(!ClsToWrite.contains(meth.typeid)) ClsToWrite.add(meth.typeid);
	}*/

	// A method to get type of class in IR
	String getType(String s){
		return typeMap.containsKey(s) ? typeMap.get(s) : "%class."+s;
	}
	String getTypePointer(String s){
		return getType(s)+"*";
	}

	// To Emit methods
	private void writeMethod(PrintWriter out, AST.method meth, ClassPlus cls){
		//Pre-defined methods that are not overriden
		if(meth.body instanceof AST.no_expr){
			if(meth.name.equals("abort")){// Working
				out.print("\ndefine %class.Object @abort_" + cls.name + "(" + getType(cls.name)+"* %this " + "){\nentry:");
				out.println("call void @exit(i32 0)\n");
				out.println("ret %class.Object " + writeLoad(out, "%1", writeAlloc(out, new IntPointer(), "%class.Object"), "Object") + "\n}\n");
			}
			else if(meth.name.equals("type_name")){// Working
				out.print("\ndefine i8* @type_name_" + cls.name + "(" + getType(cls.name)+"* %this " + "){\nentry:\n");
				buffer += "@str."+(global++)+" = private unnamed_addr constant ["+(cls.name.length()+1)+" x i8] c\"" + writeStr(cls.name) + "\\00\", align 1\n";
				out.println("%0 = bitcast [" + (cls.name.length()+1) + " x i8]* @str." + (global-1) + " to i8* ");
				out.println("ret i8* %0\n}");
				out.print(buffer);
			}
			else if(meth.name.equals("length")){// Working
				out.println("\ndefine i32 @length_" + cls.name + "( i8** ){\nentry:");
				out.println("%2 = call i64 @strlen( i8* " + writeLoad(out, "%1", "%0", "String") + " )");
				out.println("%3 = trunc i64 %2 to i32\n");
				out.println("ret i32" + " %3\n}");
			}
			else if(meth.name.equals("concat")){// working
				out.println("\ndefine i8* @concat_" + cls.name + "( i8**, i8** ){\nentry:");
				writeLoad(out, "%2", "%0", "String");
				writeLoad(out, "%3", "%1", "String");
				out.println("%4 = call i8* @malloc( i32 1024 )");
				out.println("%5 = call i8* @strcpy( i8* %4, i8* %2 )");
				out.println("%6 = call i8* @strcat( i8* %5, i8* %3 )");
				out.println("ret i8* %6\n}");
			}
			else if(meth.name.equals("substr")){// working
				out.println("@str."+(global++)+" = private unnamed_addr constant [1 x i8] zeroinitializer, align 1");
				out.println("\ndefine i8* @substr_" + cls.name + "( i8**, i32* %sp, i32* %lp){\nentry:");
				writeLoad(out, "%start", "%sp", "Int");
				writeLoad(out, "%len", "%lp", "Int");
				out.println("%sps = load i8*, i8** %0");
				out.println("%1 = getelementptr inbounds i8, i8* %sps, i32 %start");
				out.println("%2 = call i8* @malloc( i32 1024 )");
				out.println("%3 = call i8* @strncpy( i8* %2, i8* %1, i32 %len )");
				out.println("%4 = getelementptr inbounds [1 x i8], [1 x i8]* @str." + (global-1) + ", i32 0, i32 0");
				out.println("%5 = call i8* @strcat( i8* %3, i8* %4 )");
				out.println("ret i8* %5\n}");
			}
			else if(meth.name.equals("out_string")){// Working
				out.println("\ndefine " + getType(cls.name) + " @out_string_" + cls.name + "( " + getType(cls.name) + "* %this, i8** %x){\nentry:");
				writeLoad(out, "%0", "%x", "String");
				out.println("%1 = call i32 (i8*, ...) @printf( i8* bitcast ( [3 x i8]* @strfors to i8* ), i8* %0 )");
				out.println("ret " + getType(cls.name) + " " + writeLoad(out, "%2", "%this" ,cls.name) + " \n}");
				//out.println("attributes #1 = { \"correctly-rounded-divide-sqrt-fp-math\"=\"false\" \"disable-tail-calls\"=\"false\" \"less-precise-fpmad\"=\"false\" \"no-frame-pointer-elim\"=\"true\" \"no-frame-pointer-elim-non-leaf\" \"no-infs-fp-math\"=\"false\" \"no-nans-fp-math\"=\"false\" \"no-signed-zeros-fp-math\"=\"false\" \"no-trapping-math\"=\"false\" \"stack-protector-buffer-size\"=\"8\" \"target-cpu\"=\"x86-64\" \"target-features\"=\"+fxsr,+mmx,+sse,+sse2,+x87\" \"unsafe-fp-math\"=\"false\" \"use-soft-float\"=\"false\" }");
				if(!typeMap.containsKey("@strfors")){
					out.println("@strfors = private unnamed_addr constant [3 x i8] c\"%s\\00\"\n");
					typeMap.put("@strfors", "[3 x i8]*");
				}
			}
			else if(meth.name.equals("out_int")){// Working
				out.println("\ndefine " + getType(cls.name) + " @out_int_" + cls.name + "( " + getType(cls.name) + "* %this, i32* %x) #0{\nentry:");
				writeLoad(out, "%0", "%x", "Int");
				out.println("%1 = call i32 (i8*, ...) @printf( i8* bitcast ( [3 x i8]* @strfori to i8* ), i32 %0 )");
				out.println("ret " + getType(cls.name) + " " + writeLoad(out, "%2", "%this" ,cls.name) + " \n}");
				if(!typeMap.containsKey("@strfori")){
					out.println("@strfori = private unnamed_addr constant [3 x i8] c\"%d\\00\"\n");
					typeMap.put("@strfori", "[3 x i8]*");
				}
			}
			else if(meth.name.equals("in_int")){// Working
				out.println("\ndefine i32 @in_int_" + cls.name + "( " + getType(cls.name) + "* %this ) #0{\nentry:");
				out.println("%0 = call i8* @malloc( i32 4 )");
				out.println("%1 = bitcast i8* %0 to i32*");
				out.println("%2 = call i32 (i8*, ...) @scanf( i8* bitcast ( [3 x i8]* @strfori to i8* ), i32* %1 )");
				out.println("ret i32 " + writeLoad(out, "%3", "%1", "Int") + "\n}");
				if(!typeMap.containsKey("@strfori")){
					out.println("@strfori = private unnamed_addr constant [3 x i8] c\"%d\\00\"\n");
					typeMap.put("@strfori", "[3 x i8]*");
				}
			}
			else if(meth.name.equals("in_string")){// Working
				out.println("\ndefine i8* @in_string_" + cls.name + "( " + getType(cls.name) + "* %this ) #0{\nentry:");
				out.println("%0 = call i8* @malloc( i32 1024 )");
				out.println("%1 = call i32 (i8*, ...) @scanf( i8* bitcast ( [3 x i8]* @strfors to i8* ), i8* %0 )");
				out.println("ret i8* %0\n}");
				if(!typeMap.containsKey("@strfors")){
					out.println("@strfors = private unnamed_addr constant [3 x i8] c\"%s\\00\"\n");
					typeMap.put("@strfors", "[3 x i8]*");
				}
			}
		}
		else{
			out.print("\ndefine " + getType(meth.typeid) + "* @" + (meth.name.equals("main") && cls.name.equals("Main") ? "main" : (meth.name + "_" + cls.name)) + "(");
			HashMap<String,Integer> aMap = new HashMap<String,Integer>();
			String s = "";
			s += getType(cls.name)+"* %this, ";
			int i = 0;
			for(AST.formal f: meth.formals){
				s += getType(f.typeid)+"*, ";
				aMap.put(f.name, i++);
			}
			if (!s.equals("")) s = s.substring(0, s.length() - 2);
			s += ") #0 {\nentry:";
			out.println(s);
			s = writeMthdBody(out, cls, meth, aMap, i);
			out.println("}");
			out.println(s);
		}
		buffer = "";
	}
	private String writeMthdBody(PrintWriter out, ClassPlus cls, AST.method mthd, HashMap<String,Integer> aMap, int i){
		IntPointer varName = new IntPointer(i);
		String val = "";
		ArrayList<String> anames = new ArrayList<String>(cls.alist.keySet());
		HashMap<String,String> tMap = new HashMap<String,String>();
		String thisVar = "%this";

		// For main method we have initialise a 'Main' class
		if(mthd.name.equals("main") && cls.name.equals("Main")){
			String thisVarPoi = writeAlloc(out, varName, getTypePointer(cls.name));
			thisVar = writeAlloc(out, varName, getType(cls.name));
			writeStore(out, thisVar, thisVarPoi, getTypePointer(cls.name), true);
			thisVar = writeLoad(out, varName, thisVarPoi, getTypePointer(cls.name), true);
			/*int k = 0;
			for(String a: classMap.get("Main").alist.keySet()){
				AST.attr tempat = classMap.get("Main").alist.get(a);
				if(!typeMap.containsKey(tempat.typeid)){
					k++;
					String tempatalloc = writeAlloc(out, varName, getType(tempat.typeid));
					out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (cls.name) + ", %class." + (cls.name) + "* " + thisVar + ", i32 0, i32 " + (attrNumMap.get("Main").get(tempat.name)-k) + "; " + tempat.name);
					writeStore(out, tempatalloc, "%" + (varName.value-1), getTypePointer(tempat.typeid), true);
				}
			}*/
			out.println("call void @INIT_" + cls.name + "(" + getTypePointer(cls.name) + " " + thisVar + ")");
		}
		writeStore(out, thisVar, writeAlloc(out, varName, getTypePointer(cls.name)), getTypePointer(cls.name), true);
		writeLoad(out, varName, "%"+(varName.value-1), getTypePointer(cls.name), true);
		aMap.put("self", varName.value-1);
		tMap.put("self", cls.name);

		for(int j=0;j<cls.alist.size();++j) {
			AST.attr attr = cls.alist.get(anames.get(j));
			// Pointer to a variable is stored in the previous instruction
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (cls.name) + ", %class." + (cls.name) + "* " + thisVar + ", i32 0, i32 " + j + "; " + attr.name);
			if(!typeMap.containsKey(attr.typeid)) writeLoad(out, varName, "%" + (varName.value-1), getTypePointer(attr.typeid), true);
			aMap.put(attr.name, varName.value-1);
			tMap.put(attr.name, attr.typeid);
		}
		int in=0;
		for(AST.formal f: mthd.formals){
			tMap.put(f.name, f.typeid);
			if(typeMap.containsKey(f.typeid)){
				writeStore(out, writeLoad(out, varName, "%" + in++, f.typeid), varName, f.typeid);
			}
			else{
				writeStore(out, "%" + in++, writeAlloc(out, varName, getTypePointer(f.typeid)), getTypePointer(f.typeid), true);
				writeLoad(out, varName, "%"+(varName.value-1), getTypePointer(f.typeid), true);
			}
			aMap.put(f.name, varName.value-1);
			//if(mthd.name.equals("g")) System.out.println(f.name + " got " + aMap.get(f.name));
		}
		if(mthd.body.type.equals(mthd.typeid)){
			val = evalExpr(cls, mthd.body, out, varName, aMap, tMap, true);
			out.println("ret " + getType(mthd.typeid) + "* " + val);
		}
		else{
			val = evalExpr(cls, mthd.body, out, varName, aMap, tMap, true);
			addCast(mthd.typeid, mthd.body.type);
			String retVar = writeAlloc(out, varName, getType(mthd.typeid));
			out.println("call void @CAST_" + mthd.typeid + "_" + mthd.body.type + "( " + getType(mthd.typeid) + "* " + retVar + ", " + getTypePointer(mthd.body.type) + " " + val + " )");
			out.println("ret " + getType(mthd.typeid) + "* " + retVar);
		}
		return buffer;
	}
	private void writeCaster(PrintWriter out, ClassPlus clsPar, ClassPlus clsChild){
		out.print("\ndefine void @CAST_" + clsPar.name + "_" + clsChild.name + "("); // No method is gaurenteed to have this name as all features start with small letters
		String s = "";
		s += getType(clsPar.name)+"* %par, ";
		s += getType(clsChild.name)+"* %chld, ";
		if (!s.equals("")) s = s.substring(0, s.length() - 2);
		s += ") #0 {\nentry:";
		out.println(s);
		IntPointer varName = new IntPointer();
		ArrayList<String> aparnames = new ArrayList<String>();
		aparnames.addAll(clsPar.alist.keySet());
		ArrayList<String> achldnames = new ArrayList<String>();
		achldnames.addAll(clsPar.alist.keySet());
		if(!achldnames.equals(aparnames)) System.out.println("Error casting");
		int chldOffset = 0;
		for(int i=0; i<aparnames.size(); ++i){
			AST.attr presA = clsPar.alist.get(aparnames.get(i));
			String atype = presA.typeid;
			if(!typeMap.containsKey(atype)) chldOffset++;
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (clsPar.name) + ", %class." + (clsPar.name) + "* %par, i32 0, i32 " + i);
			//System.out.println("par-"+clsPar.alist.get(anames.get(i)));
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (clsChild.name) + ", %class." + (clsChild.name) + "* %chld, i32 0, i32 " + (attrNumMap.get(clsChild.name).get(presA.name) - chldOffset));
			//System.out.println("child-"+clsChild.alist.keySet().get(anames.get(i)));
			writeStore(out, writeLoad(out, varName, "%" + (varName.value-1), (typeMap.containsKey(atype)) ? getType(atype) : getTypePointer(atype), true), "%" + (varName.value-3), (typeMap.containsKey(atype)) ? getType(atype) : getTypePointer(atype), true);
			//out.println("store " + atype + " %" + (varName.value-1) + ", " + atype + "* %" + (varName.value-3));
			if(!ClsToWrite.contains(atype)) ClsToWrite.add(atype);
		}
		out.println("ret void\n}");
		buffer = "";
	}

	// To emit class definitions
	private void writeClass(PrintWriter out, ClassPlus cls){
		//checkForWrite(cls);
		out.print("\n%class."+cls.name+" = type{ ");
		String s = "";
		for(AST.attr a: cls.alist.values()) s += getType(a.typeid) + (typeMap.containsKey(a.typeid) ? "" : "*") +", ";
		if(!s.isEmpty()) s = s.substring(0, s.length() - 2);
		out.println(s+" }");
	}

	private void writeConstructor(PrintWriter out, ClassPlus cls) {
		out.print("\ndefine void @INIT_" + cls.name); //No method is gaurenteed to have this name as all features start with small letters
		out.print(" ( %class." + cls.name + "*" + " %this" + " ) { \n");
		out.println("entry: ");
		IntPointer varName = new IntPointer();
		ArrayList<String> anames = new ArrayList<String>(cls.alist.keySet());
		HashMap<String,Integer> amap = new HashMap<String,Integer>();
		HashMap<String,String> tmap = new HashMap<String,String>();
		for(int i=0;i<cls.alist.size();++i) {
			AST.attr attr = cls.alist.get(anames.get(i));
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (cls.name) + ", %class." + (cls.name) + "* %this, i32 0, i32 " + i + "; " + attr.name);
			/*if(!typeMap.containsKey(attr.typeid)){
				String tempatalloc = writeAlloc(out, varName, getType(attr.typeid));
				writeStore(out, tempatalloc, "%" + (varName.value-2), getTypePointer(attr.typeid), true);
			}*/
			if(!typeMap.containsKey(attr.typeid)) writeLoad(out, varName, "%" + (varName.value-1), getTypePointer(attr.typeid), true);
			amap.put(attr.name, varName.value-1);
			tmap.put(attr.name, attr.typeid);
		}
		attrNumMap.put(cls.name, amap);
		for(int i=0;i<cls.alist.size();++i) {
			AST.attr attr = initOrder.get(cls.name).get(i); // Initialisation shld be done in this order
			Integer aName = amap.get(attr.name);
			if(attr.value.type.equals("_no_type")){
				if(attr.typeid.equals("Int")){
					writeStore(out, "0", "%"+aName, "Int");
					//out.println("store i32 0, i32* %"+(aName)+", align 4");
				}
				else if(attr.typeid.equals("Bool")){
					writeStore(out, "0", "%"+aName, "Bool");
					//out.println("store i8 0, i8* %"+(aName)+", align 4");
				}
				else if(attr.typeid.equals("String")){
					buffer += "@str."+(global++)+" = private unnamed_addr constant [1 x i8] zeroinitializer, align 1";
					out.println("store i8* getelementptr inbounds ([1 x i8], [1 x i8]* @str." + (global-1) + ", i32 0, i32 0), i8** %"+ (aName) +", align 8");
				}
				else{
					writeStore(out, "null", "%"+(aName-1), getTypePointer(attr.typeid), true); // Write 'null' to check for void
				}
			}
			else{
				String val = "";
				if(attr.typeid.equals("Int")){
					val = evalExpr(cls, attr.value, out, varName, amap, tmap, false);
					writeStore(out, val, "%"+aName, "Int");
					//out.println("store i32 " + val + ", i32* %"+(aName)+", align 4");
				}
				else if(attr.typeid.equals("Bool")){
					val = evalExpr(cls, attr.value, out, varName, amap, tmap, false);
					writeStore(out, val, "%"+aName, "Bool");
					//out.println("store i8 "+ val +", i8* %"+(aName)+", align 4");
				}
				else if(attr.typeid.equals("String")){
					val = evalExpr(cls, attr.value, out, varName, amap, tmap, false);
					out.println("store i8* %" + (varName.value-1) + ", i8** %"+ (aName) +", align 8");
				}
				else{
					if(!attr.value.type.equals(attr.typeid)){
						val = evalExpr(cls, attr.value, out, varName, amap, tmap, true);
						addCast(attr.typeid, attr.value.type);
						out.println("call void @CAST_" + attr.typeid + "_" + attr.value.type + "(" + getTypePointer(attr.typeid) + " %" + amap.get(attr.name) + ", " + getTypePointer(attr.value.type) + " " + val + ")");
					}
					else{
						val = evalExpr(cls, attr.value, out, varName, amap, tmap, true);
						out.println("store " + getType(attr.typeid) + "* " + val + ", " + getType(attr.typeid) + "** %" + (aName-1));
					}
				}
			}
		}
		out.println("ret void\n}");
		out.println(buffer);
		buffer = "";
	}

	// A method to write a load instruction into a old variable
	private String writeLoad(PrintWriter out, String a, String b, String type){
		out.println(a + " = load " + getType(type) + ", " + getType(type) + "* " + b);
		return a;
	}
	// A overload to write a load instruction into a new variable
	private String writeLoad(PrintWriter out, IntPointer a, String b, String type){
		out.println("%" + (a.value++) + " = load " + getType(type) + ", " + getType(type) + "* " + b);
		return "%"+(a.value-1);
	}
	// A overload to write a load instruction into a variable without calling getType
	private String writeLoad(PrintWriter out, IntPointer a, String b, String type, boolean typeFlag){
		out.println("%" + (a.value++) + " = load " + type + ", " + type + "* " + b);
		return "%"+(a.value-1);
	}
	// A method to write a store instruction into a old variable
	private String writeStore(PrintWriter out, String a, String b, String type){
		out.println("store " + getType(type) + " " + a + ", " + getType(type) + "* " + b);
		return a;
	}
	// A method to write a store instruction into a new variable
	private String writeStore(PrintWriter out, String a, IntPointer b, String type){
		out.println("%" + (b.value++) + " = alloca " + getType(type) + ", align 8");
		out.println("store " + getType(type) + " " + a + ", " + getType(type) + "* %" + (b.value-1));
		return "%"+(b.value-1);
	}
	private String writeStore(PrintWriter out, String a, String b, String type, boolean typeFlag){
		out.println("store " + type + " " + a + ", " + type + "* " + b);
		return a;
	}
	// A method to write a alloca instruction
	private String writeAlloc(PrintWriter out, IntPointer b, String typeString){
		out.println("%" + (b.value++) + " = alloca " + typeString);
		return "%"+(b.value-1);
	}
	// String constants in IR require some formatting, this fn returns formatted strings
	private String writeStr(String str){
		String finalStr = "";
		for(int i=0; i<str.length(); ++i){
			char ch = str.charAt(i);
			if(ch == ('\n'))	finalStr += "\\0A";
			else if(ch == ('\t'))	finalStr += "\\09";
			else if(ch == ('\\'))	finalStr += "\\5C";
			else if(ch == ('\"'))	finalStr += "\\22";
			else finalStr += ch;
			System.out.println("ch - " + ch);
		}
		return finalStr;
	}

	// This function can be recursively called to evaluate expressions and writing instructions to IR
	// The bool argument can be changed to get the result value or get a pointer to the result value( which is useful in static dispatch)
	// The intpointer argument keeps track of recent instruction no. which can be used to write further instructions
	// aMap contains map from attribute name to instruction no. it is stored in
	// tMap contains map from attribute name to attribute type
	private String evalExpr(ClassPlus cls, AST.expression expr, PrintWriter out, IntPointer varNameStart, HashMap<String,Integer> aMap, HashMap<String,String> tMap, boolean needPointer){
		if(expr instanceof AST.string_const){
			String val = ((AST.string_const) expr).value;
			buffer += "@str."+(global++)+" = private unnamed_addr constant ["+(val.length()+1)+" x i8] c\"" + writeStr(val) + "\\00\", align 1\n";
			typeMap.put("@str." + (global-1), "[" + (val.length()+1) + " x i8]*");
			out.println("%" + (varNameStart.value++) + " = bitcast " + typeMap.get("@str." + (global-1)) + " " + "@str." + (global-1) + " to i8*");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "String") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.int_const){
			return needPointer ? writeStore(out, String.valueOf(((AST.int_const) expr).value), varNameStart, "Int") : String.valueOf(((AST.int_const) expr).value);
		}
		else if(expr instanceof AST.bool_const){
			String val = ((AST.bool_const) expr).value ? "1" : "0";
			return needPointer ? writeStore(out, val, varNameStart, "Int") : val ;
		}
		else if(expr instanceof AST.comp){
			AST.comp finalExpr = (AST.comp) expr;
			String val = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = xor i1 " + val + ", true");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.eq){
			AST.eq finalExpr = (AST.eq) expr;
			String val1 = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, tMap, false);
			String val2 = evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = icmp eq i32 " + val1 + ", " + val2);
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.leq){
			AST.leq finalExpr = (AST.leq) expr;
			String val1 = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, tMap, false);
			String val2 = evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = icmp slt i32 " + val1 + ", " + val2);
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.neg){
			AST.neg finalExpr = (AST.neg) expr;
			String val = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = mul nsw i32 " + val + ", -1");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Int") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.plus){
			AST.plus finalExpr = (AST.plus) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = add nsw i32 " + left + ", " + right);
			return needPointer ? writeStore(out, "%"+String.valueOf(varNameStart.value-1), varNameStart, "Int") : "%"+String.valueOf(varNameStart.value-1);
		}
		else if(expr instanceof AST.sub){
			AST.sub finalExpr = (AST.sub) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = sub nsw i32 " + left + ", " + right);
			return needPointer ? writeStore(out, "%"+String.valueOf(varNameStart.value-1), varNameStart, "Int") : "%"+String.valueOf(varNameStart.value-1);
		}
		else if(expr instanceof AST.mul){
			AST.mul finalExpr = (AST.mul) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, tMap, false);
			out.println("%" + (varNameStart.value++) + " = mul nsw i32 " + left + ", " + right);
			return needPointer ? writeStore(out, "%"+String.valueOf(varNameStart.value-1), varNameStart, "Int") : "%"+String.valueOf(varNameStart.value-1);
		}
		else if(expr instanceof AST.divide){
			AST.divide finalExpr = (AST.divide) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, tMap, false);
			String divRes = writeAlloc(out, varNameStart, "i32");
			// Error handling by using a if condition and static dispatch to print error message
			if(!(finalExpr.e2 instanceof AST.int_const) || (finalExpr.e2 instanceof AST.int_const && evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, tMap, false).equals("0"))){
				out.println("%" + (varNameStart.value++) + " = icmp ne i32 0, " + right);
				out.print("br i1 %" + (varNameStart.value-1) + ", label %" + (varNameStart.value++) + ", label %" + (varNameStart.value+1) + "\n");
			}// 					0										1											3
			out.println("%" + (varNameStart.value++) + " = sdiv i32 " + left + ", " + right);
			writeStore(out, "%" + (varNameStart.value-1), divRes, "Int");

			if(!(finalExpr.e2 instanceof AST.int_const) || (finalExpr.e2 instanceof AST.int_const && evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, tMap, false).equals("0"))){
				out.println("br label %" + (varNameStart.value+6));
				varNameStart.value++;
				AST.string_const detErr = (AST.string_const) divErr.actuals.get(0);
				detErr.value += " on line " + finalExpr.lineNo + "\n";
				evalExpr(cls, divErr, out, varNameStart, aMap, tMap, needPointer);
				out.println("call void @exit(i32 1)\n");
				out.println("br label %" + (varNameStart.value++));
				detErr.value = "Error: Division by 0";
			}
			return needPointer ? divRes : writeLoad(out, varNameStart, divRes, "Int");
		}
		else if(expr instanceof AST.object){
			Integer valAttr = aMap.get(((AST.object) expr).name);
			//out.println("%" + (varNameStart.value++) + " = load " + getType(expr.type) + ", " + getType(expr.type) + "* %" + valAttr);
			return needPointer ? "%"+valAttr : writeLoad(out, varNameStart, "%"+valAttr, ((AST.object) expr).type);
		}
		else if(expr instanceof AST.isvoid){
			AST.isvoid finalExpr = (AST.isvoid) expr;
			String e1 = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, tMap, true);
			out.println("%" + (varNameStart.value++) + " = icmp eq " + getType(finalExpr.e1.type) + "* " + e1 + ", null");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.assign){
			AST.assign finalExpr = (AST.assign) expr;
			String right = "";
			int varNum = aMap.get(finalExpr.name) - (typeMap.containsKey(tMap.get(finalExpr.name)) ? 0 : 1);
			System.out.println("Assgn ");
			// Non-basic types have pointer to pointers, so we need separate ways to handle basic and non-basic types
			if(!tMap.get(finalExpr.name).equals(finalExpr.e1.type)){
				right = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, true);
				addCast(tMap.get(finalExpr.name), finalExpr.e1.type);
				String allocPoi = writeAlloc(out, varNameStart, getType(tMap.get(finalExpr.name)));
				out.println("call void @CAST_" + tMap.get(finalExpr.name) + "_" + finalExpr.e1.type + "( " + getType(tMap.get(finalExpr.name)) + "* " + allocPoi + ", " + getTypePointer(finalExpr.e1.type) + " " + right + " )");
				String loaded = writeLoad(out, varNameStart, allocPoi, tMap.get(finalExpr.name));
				writeStore(out, loaded, "%" + aMap.get(finalExpr.name), tMap.get(finalExpr.name));
				return needPointer ? right : loaded;
			}
			else if(!typeMap.containsKey(tMap.get(finalExpr.name))){
				right = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, true);
				System.out.println("right - " + right + " - varnum - " + varNum);
				writeStore(out, right, "%" + varNum, getTypePointer(tMap.get(finalExpr.name)), true);
				return needPointer ? right : writeLoad(out, varNameStart, right, getType(finalExpr.e1.type));
				//out.println("store " + getType(finalExpr.type) + " " + right + ", " + getType(finalExpr.type) + "* %" + varNum + ", align 4");
			}
			else{
				right = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, tMap, false);
				writeStore(out, right, "%"+varNum, getType(finalExpr.type), true);
				return needPointer ? "%"+varNum : right;
				//out.println("store " + getType(finalExpr.type) + " " + right + ", " + getType(finalExpr.type) + "* %" + varNum + ", align 4");
			}
		}
		else if(expr instanceof AST.new_){
			AST.new_ finalExpr = (AST.new_) expr;
			String newVar = "";
			if(finalExpr.type.equals("Int")){
				if(needPointer){
					newVar = writeAlloc(out, varNameStart, getType(finalExpr.type));
					return writeStore(out, "0", newVar, "Int");
				}
				return "0";
			}
			else if(finalExpr.type.equals("Bool")){
				if(needPointer){
					newVar = writeAlloc(out, varNameStart, getType(finalExpr.type));
					return writeStore(out, "0", newVar, "Bool");
				}
				return "0";
			}
			else if(finalExpr.type.equals("String")){
				System.out.println("new str");
				buffer += "@str."+(global++)+" = private unnamed_addr constant [1 x i8] zeroinitializer, align 1";
				typeMap.put("@str." + (global-1), "[1 x i8]*");
				out.println("%" + (varNameStart.value++) + " = bitcast [1 x i8]* @str." + (global-1) + " to i8*");
				return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "String") : "%" + (varNameStart.value-1);

			}
			else{
				//out.println("%" + (varNameStart.value++) + " = alloca " + getType(finalExpr.type));
				newVar = writeAlloc(out, varNameStart, getType(finalExpr.type));
				out.println("call void @INIT_" + finalExpr.type + "(" + getTypePointer(finalExpr.type) + " " + newVar + ")");
				return needPointer ? newVar : writeLoad(out, varNameStart, newVar, finalExpr.type);
			}
		}
		else if(expr instanceof AST.block){
			AST.block finalExpr = (AST.block) expr;
			String val = "";
			for(AST.expression e: finalExpr.l1){
				val = evalExpr(cls, e, out, varNameStart, aMap, tMap, needPointer);
			}
			return val;
		}
		else if(expr instanceof AST.static_dispatch){
			AST.static_dispatch finalExpr = (AST.static_dispatch) expr;
			ClassPlus callingCls = classMap.get(finalExpr.typeid);
			AST.method callingMeth = callingCls.mlist.get(finalExpr.name);
			mthdsToWrite.put(callingMeth, callingCls);
			System.out.println("Stat dsi on " + callingCls.name + " on " + callingMeth.name);
			String callStr = "";
			// The actuals are evaluated first and then the caller
			for(int i = 0; i < callingMeth.formals.size() ; ++i){
				String val = evalExpr(cls, finalExpr.actuals.get(i), out, varNameStart, aMap, tMap, true);
				if(!finalExpr.actuals.get(i).type.equals(callingMeth.formals.get(i).typeid)){
					addCast(callingMeth.formals.get(i).typeid, finalExpr.actuals.get(i).type);
					String castVal = writeAlloc(out, varNameStart, getType(callingMeth.formals.get(i).typeid));
					out.println("call void @CAST_" + callingMeth.formals.get(i).typeid + "_" + finalExpr.actuals.get(i).type + "( " + getType(callingMeth.formals.get(i).typeid) + "* " + castVal + ", " + getTypePointer(finalExpr.actuals.get(i).type) + " " + val + " )");
					val = castVal;
				}
				callStr += ", " + getType(callingMeth.formals.get(i).typeid) + "* " + val; 
			}
			String callVarName = evalExpr(cls, finalExpr.caller, out, varNameStart, aMap, tMap,true);
			String retType = callingMeth.typeid.equals("SELF_TYPE") ? finalExpr.typeid : callingMeth.typeid;
			// AST.new_ cannot be void
			if(!(finalExpr.caller instanceof AST.new_)){
				mthdsToWrite.put(classMap.get("IO").mlist.get("out_string"), classMap.get("IO"));
				out.println("%" + (varNameStart.value++) + " = icmp eq " + getType(finalExpr.caller.type) + "* " + callVarName + ", null");
				out.println("br i1 %" + (varNameStart.value-1) + ", label %" + (varNameStart.value++) + ", label %" + (varNameStart.value+4));
				out.println("%" + (varNameStart.value++) + " = alloca %class.IO");
				String ioalloc = "%" + (varNameStart.value-1);
				out.println("call void @INIT_IO(%class.IO* %" + (varNameStart.value-1) + ")");
				String detailErr = voidErr + " on line " + finalExpr.caller.lineNo + "\n";
				buffer += "@str."+(global++)+" = private unnamed_addr constant ["+(detailErr.length()+1)+" x i8] c\"" + writeStr(detailErr) + "\\00\", align 1\n";
				typeMap.put("@str." + (global-1), "[" + (detailErr.length()+1) + " x i8]*");
				out.println("%" + (varNameStart.value++) + " = bitcast " + typeMap.get("@str." + (global-1)) + " " + "@str." + (global-1) + " to i8*");
				out.println("%" + (varNameStart.value++) + " = alloca i8*, align 8");
				out.println("store i8* %" + (varNameStart.value-2) + ", i8** %" + (varNameStart.value-1));
				out.println("%" + (varNameStart.value++) + " = call %class.IO @out_string_IO(%class.IO* " + ioalloc + ", i8** %" + (varNameStart.value-2) + ")");
				out.println("call void @exit(i32 1)");
				out.println("br label %" + (varNameStart.value++));
			}
			if(!finalExpr.typeid.equals(finalExpr.caller.type)){
				addCast(finalExpr.typeid, finalExpr.caller.type);
				String callCastVal = writeAlloc(out, varNameStart, getType(finalExpr.typeid));
				out.println("call void @CAST_" + finalExpr.typeid + "_" + finalExpr.caller.type + "( " + getType(finalExpr.typeid) + "* " + callCastVal + ", " + getTypePointer(finalExpr.caller.type) + " " + callVarName + " )");
				callVarName = callCastVal;
			}
			// Pre-defined methods return values not pointers, so they are handled differently
			boolean predef = preDefMthds.contains(finalExpr.name) && callingCls.mlist.get(finalExpr.name).body instanceof AST.no_expr;
			callStr = ("call " + getType(retType) + (predef ? "" : "*") + " @" + finalExpr.name + "_" + finalExpr.typeid + "(" + getTypePointer(callingCls.name) + " " + callVarName) + callStr;// Add actuals and case of strings
			out.println("%" + (varNameStart.value++) + " = " + callStr + ")");
			//return needPointer ? "%" + (varNameStart.value-1) : writeLoad(out, varNameStart, "%" + (varNameStart.value-1), callingMeth.typeid);
			if(needPointer){
				if(predef){
					return writeStore(out, "%" + (varNameStart.value-1), varNameStart, retType);
				}
				else{
					return "%" + (varNameStart.value-1);
				}
			}
			else{
				if(predef){
					return "%" + (varNameStart.value-1);
				}
				else{
					return writeLoad(out, varNameStart, "%" + (varNameStart.value-1), retType);
				}
			}
		}
		else if(expr instanceof AST.cond){
			AST.cond finalExpr = (AST.cond) expr;
			String ifbody = "", elsebody = "",ifval = "", elseval = "";
			String cond = evalExpr(cls, finalExpr.predicate, out, varNameStart, aMap, tMap, false);
			String finalVar = writeAlloc(out, varNameStart, needPointer ? getTypePointer(finalExpr.type) : getType(finalExpr.type));
			out.print("br i1 " + cond);
			int iflabel = varNameStart.value++;
			StringWriter buffStr = new StringWriter();
			PrintWriter buffer = new PrintWriter(buffStr);
			ifval = evalExpr(cls, finalExpr.ifbody, buffer, varNameStart, aMap, tMap, needPointer);
			if(!finalExpr.type.equals(finalExpr.ifbody.type)){
				addCast(finalExpr.type, finalExpr.ifbody.type);
				buffer.println("call void @CAST_" + finalExpr.type + "_" + finalExpr.ifbody.type + "( " + getType(finalExpr.type) + "* " + (varNameStart.value++) + ", " + getTypePointer(finalExpr.ifbody.type) + " " + ifval + " )");
				ifval = "%" + (varNameStart.value-1);
			}
			if(needPointer) buffer.println("store " + getTypePointer(finalExpr.type) + " " + ifval + ", " + getTypePointer(finalExpr.type) + "* " + finalVar);
			else writeStore(buffer, ifval, finalVar, finalExpr.type);
			int elselabel = varNameStart.value++;
			ifbody = buffStr.toString();
			buffStr = new StringWriter();
			buffer = new PrintWriter(buffStr); // This printwriter stores the instructions written while evaluating the ifbody and elsebody
			elseval = evalExpr(cls, finalExpr.elsebody, buffer, varNameStart, aMap, tMap, needPointer);
			if(!finalExpr.type.equals(finalExpr.elsebody.type)){
				addCast(finalExpr.type, finalExpr.elsebody.type);
				buffer.println("call void @CAST_" + finalExpr.type + "_" + finalExpr.elsebody.type + "( " + getType(finalExpr.type) + "* " + (varNameStart.value++) + ", " + getTypePointer(finalExpr.elsebody.type) + " " + elseval + " )");
				elseval = "%" + (varNameStart.value-1);
			}
			if(needPointer) buffer.println("store " + getTypePointer(finalExpr.type) + " " + elseval + ", " + getTypePointer(finalExpr.type) + "* " + finalVar);
			else writeStore(buffer, elseval, finalVar, finalExpr.type);
			elsebody = buffStr.toString();
			out.println(", label %" + iflabel + ", label %" + elselabel);
			out.println(ifbody);
			out.println("br label %" + (varNameStart.value++));
			out.println(elsebody);
			out.println("br label %" + (varNameStart.value-1));
			buffer.close();
			return writeLoad(out, varNameStart, finalVar, needPointer ? getTypePointer(finalExpr.type) : getType(finalExpr.type), true);
		}
		else if(expr instanceof AST.loop){
			AST.loop finalExpr = (AST.loop) expr;
			out.println("br label %" + (varNameStart.value++));
			String loopStrt = "%" + (varNameStart.value-1);
			String pred = evalExpr(cls, finalExpr.predicate, out, varNameStart, aMap, tMap, false);
			out.print("br i1 " + pred + ", label %" + (varNameStart.value++) + ", label %");

			StringWriter buffStr = new StringWriter();
			PrintWriter buffer = new PrintWriter(buffStr);
			evalExpr(cls, finalExpr.body, buffer, varNameStart, aMap, tMap, false);
			buffer.println("br label " + loopStrt);
			out.println(varNameStart.value);
			out.println(buffStr.toString());
			varNameStart.value++;
			// Returns null
			return needPointer ? "null" : writeLoad(out, varNameStart, "null", "Object");
		}
		System.out.println("Unchecked case");
		return "0";
	}

	// If a attribute A's value depends on attribute B's value indexOf(A) > indexOf(B)
	// This function also calls recursively to check dependence
	private void checkExpr(ClassPlus cls, AST.expression expr, AST.attr a){
		if(expr instanceof AST.string_const || expr instanceof AST.int_const || expr instanceof AST.bool_const || expr instanceof AST.new_){
			return;
		}
		else if(expr instanceof AST.object){
			AST.attr attr = cls.alist.get(((AST.object) expr).name);
			ArrayList<AST.attr> order = initOrder.get(cls.name);
			if(order.indexOf(attr) == -1){
				order.add(order.indexOf(a),attr);
			}
			else if(order.indexOf(attr) > order.indexOf(a)){
				order.remove(a);
				order.add(order.indexOf(attr)+1, a);
			}
		}
		else if(expr instanceof AST.comp){
			AST.comp finalExpr = (AST.comp) expr;
			checkExpr(cls, finalExpr.e1, a);
		}
		else if(expr instanceof AST.eq){
			AST.eq finalExpr = (AST.eq) expr;
			checkExpr(cls, finalExpr.e1, a);
			checkExpr(cls, finalExpr.e2, a);
		}
		else if(expr instanceof AST.leq){
			AST.leq finalExpr = (AST.leq) expr;
			checkExpr(cls, finalExpr.e1, a);
			checkExpr(cls, finalExpr.e2, a);
		}
		else if(expr instanceof AST.neg){
			AST.neg finalExpr = (AST.neg) expr;
			checkExpr(cls, finalExpr.e1, a);
		}
		else if(expr instanceof AST.plus){
			AST.plus finalExpr = (AST.plus) expr;
			checkExpr(cls, finalExpr.e1, a);
			checkExpr(cls, finalExpr.e2, a);
		}
		else if(expr instanceof AST.sub){
			AST.sub finalExpr = (AST.sub) expr;
			checkExpr(cls, finalExpr.e1, a);
			checkExpr(cls, finalExpr.e2, a);
		}
		else if(expr instanceof AST.mul){
			AST.mul finalExpr = (AST.mul) expr;
			checkExpr(cls, finalExpr.e1, a);
			checkExpr(cls, finalExpr.e2, a);
		}
		else if(expr instanceof AST.divide){
			AST.divide finalExpr = (AST.divide) expr;
			checkExpr(cls, finalExpr.e1, a);
			checkExpr(cls, finalExpr.e2, a);
		}
		else if(expr instanceof AST.isvoid){
			AST.isvoid finalExpr = (AST.isvoid) expr;
			checkExpr(cls, finalExpr.e1, a);
		}
		else if(expr instanceof AST.assign){
			AST.assign finalExpr = (AST.assign) expr;
			AST.attr attr = cls.alist.get(finalExpr.name);
			ArrayList<AST.attr> order = initOrder.get(cls.name);
			if(order.indexOf(attr) == -1){
				order.add(order.indexOf(a),attr);
			}
			else if(order.indexOf(attr) > order.indexOf(a)){
				order.remove(a);
				order.add(order.indexOf(attr)+1, a);
			}
			checkExpr(cls, finalExpr.e1, a);
		}
		else if(expr instanceof AST.block){
			AST.block finalExpr = (AST.block) expr;
			for(AST.expression e: finalExpr.l1) checkExpr(cls, e, a);
		}
		else if(expr instanceof AST.static_dispatch){
			AST.static_dispatch finalExpr = (AST.static_dispatch) expr;
			checkExpr(cls, finalExpr.caller, a);
			for(AST.expression act: finalExpr.actuals) checkExpr(cls, act, a);
		}
		else if(expr instanceof AST.cond){
			AST.cond finalExpr = (AST.cond) expr;
			checkExpr(cls, finalExpr.predicate, a);
			checkExpr(cls, finalExpr.ifbody, a);
			checkExpr(cls, finalExpr.elsebody, a);
		}
		else if(expr instanceof AST.loop){
			AST.loop finalExpr = (AST.loop) expr;
			checkExpr(cls, finalExpr.predicate, a);
			checkExpr(cls, finalExpr.body, a);
		}
	}






	// A wrapper class to make a integer into a pointer
	// As java stores this as pointer a change in a recursive call is retained unlinke in the case of passing just int.
	class IntPointer{
		public int value;
		public IntPointer(){value = 0;}
		public IntPointer(int initValue){value = initValue;}
	}
	private void addCast(String a, String b){
		if(castToWrite.containsKey(a)) castToWrite.get(a).add(b);
		else castToWrite.put(a, new HashSet<String>(Arrays.asList(b)));
	}
	// Creating classplus objects for respective classes and retaining inheritance
	// ordering attributes of each class is also done here
	private void processProgram(AST.program program){
		Queue<String> q = new LinkedList<String>();
		BaseClasses base = new BaseClasses();
		classMap.put("Object", base.ObjectPlus);
		q.offer("Object");
		inherGraph.put("Object", new ArrayList<AST.class_>(Arrays.asList(base.IO,base.String,base.Int,base.Bool)));
		for(AST.class_ c:program.classes){
			if(!inherGraph.containsKey(c.parent)) inherGraph.put(c.parent, new ArrayList<AST.class_>(Arrays.asList(c)));
			else inherGraph.get(c.parent).add(c);
		}
		while(!q.isEmpty()){
			String cpar = q.remove();
			if(inherGraph.containsKey(cpar)){
				for(AST.class_ c: inherGraph.get(cpar)){
					ClassPlus cplus = new ClassPlus(c.name, cpar, classMap.get(cpar).alist, classMap.get(cpar).mlist);
					for(AST.feature e : c.features) {
						if(e.getClass() == AST.attr.class) {
							AST.attr ae = (AST.attr) e;
							cplus.alist.put(ae.name, ae);
						}
						else if(e.getClass() == AST.method.class) {
							AST.method me = (AST.method) e;
							cplus.mlist.put(me.name, me);
						}
					}
					classMap.put(c.name, cplus);
					initOrder.put(c.name, new ArrayList<>());
					for(String aname: cplus.alist.keySet()){
						AST.attr a = cplus.alist.get(aname);
						if(!initOrder.get(c.name).contains(a)) initOrder.get(c.name).add(a);
						checkExpr(cplus, a.value, a);
					}
					q.add(c.name);
				}
			}
		}
	}
}
