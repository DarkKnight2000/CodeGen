package cool;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import cool.AST.expression;

public class Codegen{
	private ArrayList<String> ClsToWrite, baseClasses;
	private ArrayList<AST.method> allWrittenMthds;
	private HashMap<AST.method, ClassPlus> mthdsToWrite, mthdsWriting;
	private HashMap<String,ClassPlus>classMap;
	private HashMap<String,String> typeMap;
	private HashMap<String,HashSet<String>> castToWrite;
	private HashMap<String, ArrayList<AST.class_>> inherGraph;
	private int global = 0; 
	private String buffer = "";
	AST.static_dispatch divErr, voidErr;
	public Codegen(AST.program program, PrintWriter out){
		//Write Code generator code here
		out.println("; I am a comment in LLVM-IR. Feel free to remove me.");
		classMap = new HashMap<String,ClassPlus>();
		typeMap = new HashMap<String,String>();
		castToWrite = new HashMap<String,HashSet<String>>();
		mthdsToWrite = new HashMap<AST.method, ClassPlus>();
		mthdsWriting = new HashMap<AST.method, ClassPlus>();
		allWrittenMthds = new ArrayList<AST.method>();
		inherGraph = new HashMap<String, ArrayList<AST.class_>>();
		typeMap.put("Int","i32");
		typeMap.put("Bool", "i1");
		typeMap.put("String", "i8*");
		ClsToWrite = new ArrayList<String>();
		baseClasses = new ArrayList<String>(Arrays.asList("Object", "IO", "Int", "String", "Bool"));
		divErr = new AST.static_dispatch(new AST.new_("IO", 0), "IO", "out_string", new ArrayList<expression>(Arrays.asList((AST.expression) new AST.string_const("Error: Division by 0.\n", 0))), 0);
		divErr.type = "IO";
		divErr.caller.type = "IO";
		divErr.actuals.get(0).type = "String";
		voidErr = new AST.static_dispatch(new AST.new_("IO", 0), "IO", "out_string", new ArrayList<expression>(Arrays.asList((AST.expression) new AST.string_const("Error: Static Dispatch on void.\n", 0))), 0);
		voidErr.type = "IO";
		voidErr.caller.type = "IO";
		voidErr.actuals.get(0).type = "String";
		processProgram(program);

		out.println("source_filename = \""+program.classes.get(0).filename+"\"\n"+"target datalayout = \"e-m:e-i64:64-f80:128-n8:16:32:64-S128\"\ntarget triple = \"x86_64-pc-linux-gnu\"");
		ClassPlus main_class = classMap.get("Main");
		ClsToWrite.addAll(classMap.keySet());
		mthdsWriting.put(main_class.mlist.get("main"), main_class);
		for(ClassPlus cls: classMap.values()){
			if(!typeMap.containsKey(cls.name)) writeClass(out, cls);
		}
		for(ClassPlus cls: classMap.values()){
			if(!typeMap.containsKey(cls.name)) writeConstructor(out, cls);
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
		out.println("define %class.Object* @createVoid() #0 {\nentry:\n" + 
		"%0 = alloca %class.Object\n" +
		"ret %class.Object* %0 \n}");
		out.println("declare void @exit(i32)");
		out.println("declare i32 @scanf(i8*, ...) #1");
		out.println("declare i32 @printf(i8*, ...) #1");
		out.println("declare i8* @malloc(i64) #1");
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
	String getType(String s){
		return typeMap.containsKey(s) ? typeMap.get(s) : "%class."+s;
	}
	String getTypePointer(String s){
		return getType(s)+"*";
	}
	private void writeMethod(PrintWriter out, AST.method meth, ClassPlus cls){
		//checkForWrite(meth);
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
		else if(meth.name.equals("concat")){// Not working
			out.println("\ndefine i8* @concat_" + cls.name + "( i8**, i8** ){\nentry:");
			out.println("%4 = call i8* @strcat( i8* " + writeLoad(out, "%2", "%0", "String") + ", i8* " + writeLoad(out, "%3", "%1", "String") + " )");
			//out.println("%5 = call i8* @malloc( i64 128 )");
			out.println("%5 = bitcast i8* %4 to i8*");
			out.println("ret i8* %5\n}");
		}
		else if(meth.name.equals("substr")){// Not working
			out.println("\ndefine i8* @substr_" + cls.name + "( i8**, i32* %sp, i32* %lp){\nentry:");
			writeLoad(out, "%start", "%sp", "Int");
			writeLoad(out, "%len", "%lp", "Int");
			out.println("%sps = getelementptr inbounds i8*, i8** %0, i32 0, i32 0");
			out.println("%1 = getelementptr inbounds i8, i8* %sps, i32 0, i32 %start");
			out.println("%2 = call i8* @malloc( i64 1024 )");
			out.println("%3 = call i8* @strncpy( i8* %2, i8* %0, i32 %len )");
			out.println("%4 = getelementptr inbounds [1024 x i8], [1024 x i8]* %retval, i32 0, i32 %len");
			out.println("store i8 0, i8* %4");
			out.println("ret i32" + " %3\n}");
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
			out.println("%0 = call i8* @malloc( i64 4 )");
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
			out.println("%0 = call i8* @malloc( i64 1024 )");
			out.println("%1 = call i32 (i8*, ...) @scanf( i8* bitcast ( [3 x i8]* @strfors to i8* ), i8* %0 )");
			out.println("ret i8* %0\n}");
			if(!typeMap.containsKey("@strfors")){
				out.println("@strfors = private unnamed_addr constant [3 x i8] c\"%s\\00\"\n");
				typeMap.put("@strfors", "[3 x i8]*");
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
		String thisVar = "%this";
		if(mthd.name.equals("main") && cls.name.equals("Main")){
			thisVar = writeAlloc(out, varName, getType(cls.name));
		}
		out.println("call void @INIT_" + cls.name + "(" + getTypePointer(cls.name) + " " + thisVar + ")");
		for(int j=0;j<cls.alist.size();++j) {
			AST.attr attr = cls.alist.get(anames.get(j));
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (cls.name) + ", %class." + (cls.name) + "* " + thisVar + ", i32 0, i32 " + j + "; " + attr.name);
			aMap.put(attr.name, varName.value-1);
		}
		if(mthd.body.type.equals(mthd.typeid)){
			val = evalExpr(cls, mthd.body, out, varName, aMap, true);
			out.println("ret " + getType(mthd.typeid) + "* " + val);
		}
		else{
			val = evalExpr(cls, mthd.body, out, varName, aMap, true);
			addCast(mthd.typeid, mthd.body.type);
			String retVar = writeAlloc(out, varName, getType(mthd.typeid));
			out.println("call void @CAST_" + mthd.typeid + "_" + mthd.body.type + "( " + getType(mthd.typeid) + "* " + retVar + ", " + getTypePointer(mthd.body.type) + " " + val + " )");
			out.println("ret " + getType(mthd.typeid) + "* " + retVar);
		}
		return buffer;
	}
	private void writeCaster(PrintWriter out, ClassPlus clsPar, ClassPlus clsChild){
		out.print("\ndefine void @CAST_" + clsPar.name + "_" + clsChild.name + "(");
		String s = "";
		s += getType(clsPar.name)+"* %par, ";
		s += getType(clsChild.name)+"* %chld, ";
		if (!s.equals("")) s = s.substring(0, s.length() - 2);
		s += ") #0 {\nentry:";
		out.print(s);
		IntPointer varName = new IntPointer();
		ArrayList<String> aparnames = new ArrayList<String>();
		aparnames.addAll(clsPar.alist.keySet());
		ArrayList<String> achldnames = new ArrayList<String>();
		achldnames.addAll(clsPar.alist.keySet());
		if(!achldnames.equals(aparnames)) System.out.println("Error casting");
		for(int i=0; i<aparnames.size(); ++i){
			String atype = clsPar.alist.get(aparnames.get(i)).typeid;
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (clsPar.name) + ", %class." + (clsPar.name) + "* %par, i32 0, i32 " + i);
			//System.out.println("par-"+clsPar.alist.get(anames.get(i)));
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (clsChild.name) + ", %class." + (clsChild.name) + "* %chld, i32 0, i32 " + i);
			//System.out.println("child-"+clsChild.alist.keySet().get(anames.get(i)));
			writeStore(out, writeLoad(out, varName, "%" + (varName.value-1), atype), "%" + (varName.value-3), atype);
			//out.println("store " + atype + " %" + (varName.value-1) + ", " + atype + "* %" + (varName.value-3));
			if(!ClsToWrite.contains(atype)) ClsToWrite.add(atype);
		}
		out.println("ret void\n}");
		buffer = "";
	}
	private void writeClass(PrintWriter out, ClassPlus cls){
		//checkForWrite(cls);
		out.print("\n%class."+cls.name+" = type{ ");
		String s = "";
		for(AST.attr a: cls.alist.values()) s += getType(a.typeid) +", ";
		if(!s.isEmpty()) s = s.substring(0, s.length() - 2);
		out.println(s+" }");
	}

	private void writeConstructor(PrintWriter out, ClassPlus cls) {
		out.print("\ndefine void @INIT_" + cls.name);//No method is gaurenteed to have this name as all features start with small letters
		out.print(" ( %class." + cls.name + "*" + " %this" + " ) { \n");
		out.println("entry: ");
		IntPointer varName = new IntPointer();
		ArrayList<String> anames = new ArrayList<String>(cls.alist.keySet());
		HashMap<String,Integer> amap = new HashMap<String,Integer>();
		for(int i=0;i<cls.alist.size();++i) {
			AST.attr attr = cls.alist.get(anames.get(i));
			out.println("%" + varName.value++ + " = getelementptr inbounds %class." + (cls.name) + ", %class." + (cls.name) + "* %this, i32 0, i32 " + i + "; " + attr.name);
			amap.put(attr.name, varName.value-1);
		}
		for(int i=0;i<cls.alist.size();++i) {
			AST.attr attr = cls.alist.get(anames.get(i));
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
			}
			else{
				String val = "";
				if(attr.typeid.equals("Int")){
					val = evalExpr(cls, attr.value, out, varName, amap, false);
					writeStore(out, val, "%"+aName, "Int");
					//out.println("store i32 " + val + ", i32* %"+(aName)+", align 4");
				}
				else if(attr.typeid.equals("Bool")){
					val = evalExpr(cls, attr.value, out, varName, amap, false);
					writeStore(out, val, "%"+aName, "Bool");
					//out.println("store i8 "+ val +", i8* %"+(aName)+", align 4");
				}
				else if(attr.typeid.equals("String")){
					val = evalExpr(cls, attr.value, out, varName, amap, false);
					out.println("store i8* %" + (varName.value-1) + ", i8** %"+ (aName) +", align 8");
				}
				else{
					if(!attr.value.type.equals(attr.typeid)){
						val = evalExpr(cls, attr.value, out, varName, amap, true);
						addCast(attr.typeid, attr.value.type);
						out.println("call void @CAST_" + attr.typeid + "_" + attr.value.type + "(" + getTypePointer(attr.typeid) + " %" + amap.get(attr.name) + ", " + getTypePointer(attr.value.type) + " " + val + ")");
					}
					else{
						val = evalExpr(cls, attr.value, out, varName, amap, false);
						out.println("store " + getType(attr.typeid) + " " + val + ", " + getType(attr.typeid) + "* %" + aName);
					}
				}
			}
		}
		out.println("ret void\n}");
		out.println(buffer);
		buffer = "";
	}

	private String writeLoad(PrintWriter out, String a, String b, String type){
		out.println(a + " = load " + getType(type) + ", " + getType(type) + "* " + b);
		return a;
	}
	private String writeLoad(PrintWriter out, IntPointer a, String b, String type){
		out.println("%" + (a.value++) + " = load " + getType(type) + ", " + getType(type) + "* " + b);
		return "%"+(a.value-1);
	}
	private String writeLoad(PrintWriter out, IntPointer a, String b, String type, boolean typeFlag){
		out.println("%" + (a.value++) + " = load " + type + ", " + type + "* " + b);
		return "%"+(a.value-1);
	}
	private String writeStore(PrintWriter out, String a, String b, String type){
		out.println("store " + getType(type) + " " + a + ", " + getType(type) + "* " + b);
		return a;
	}
	private String writeStore(PrintWriter out, String a, IntPointer b, String type){
		out.println("%" + (b.value++) + " = alloca " + getType(type) + ", align 8");
		out.println("store " + getType(type) + " " + a + ", " + getType(type) + "* %" + (b.value-1));
		return "%"+(b.value-1);
	}
	private String writeAlloc(PrintWriter out, IntPointer b, String typeString){
		out.println("%" + (b.value++) + " = alloca " + typeString);
		return "%"+(b.value-1);
	}
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

	private String evalExpr(ClassPlus cls, AST.expression expr, PrintWriter out, IntPointer varNameStart, HashMap<String,Integer> aMap, boolean needPointer){
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
		else if(expr instanceof AST.object){ // TODO: Doesnt consider attrs while writing for methods
			Integer valAttr = aMap.get(((AST.object) expr).name);
			//out.println("%" + (varNameStart.value++) + " = load " + getType(expr.type) + ", " + getType(expr.type) + "* %" + valAttr);
			return needPointer ? "%"+valAttr : writeLoad(out, varNameStart, "%"+valAttr, ((AST.object) expr).type);
		}
		else if(expr instanceof AST.comp){
			AST.comp finalExpr = (AST.comp) expr;
			String val = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = xor i1 " + val + ", true");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.eq){
			AST.eq finalExpr = (AST.eq) expr;
			String val1 = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, false);
			String val2 = evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = icmp eq i32 " + val1 + ", " + val2);
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.leq){
			AST.leq finalExpr = (AST.leq) expr;
			String val1 = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, false);
			String val2 = evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = icmp slt i32 " + val1 + ", " + val2);
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.neg){
			AST.neg finalExpr = (AST.neg) expr;
			String val = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = mul nsw i32 " + val + ", -1");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Int") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.plus){
			AST.plus finalExpr = (AST.plus) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = add nsw i32 " + left + ", " + right);
			return needPointer ? writeStore(out, "%"+String.valueOf(varNameStart.value-1), varNameStart, "Int") : "%"+String.valueOf(varNameStart.value-1);
		}
		else if(expr instanceof AST.sub){
			AST.sub finalExpr = (AST.sub) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = sub nsw i32 " + left + ", " + right);
			return needPointer ? writeStore(out, "%"+String.valueOf(varNameStart.value-1), varNameStart, "Int") : "%"+String.valueOf(varNameStart.value-1);
		}
		else if(expr instanceof AST.mul){
			AST.mul finalExpr = (AST.mul) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, false);
			out.println("%" + (varNameStart.value++) + " = mul nsw i32 " + left + ", " + right);
			return needPointer ? writeStore(out, "%"+String.valueOf(varNameStart.value-1), varNameStart, "Int") : "%"+String.valueOf(varNameStart.value-1);
		}
		else if(expr instanceof AST.divide){
			AST.divide finalExpr = (AST.divide) expr;
			String left = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, false), right = evalExpr(cls, finalExpr.e2,out,varNameStart, aMap, false);
			String divRes = writeAlloc(out, varNameStart, "i32");
			if(!(finalExpr.e2 instanceof AST.int_const) || (finalExpr.e2 instanceof AST.int_const && evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, false).equals("0"))){
				out.println("%" + (varNameStart.value++) + " = icmp ne i32 0, " + right);
				out.print("br i1 %" + (varNameStart.value-1) + ", label %" + (varNameStart.value++) + ", label %" + (varNameStart.value+1) + "\n");
			}// 					0										1											3
			out.println("%" + (varNameStart.value++) + " = sdiv i32 " + left + ", " + right);
			writeStore(out, "%" + (varNameStart.value-1), divRes, "Int");
			//
			if(!(finalExpr.e2 instanceof AST.int_const) || (finalExpr.e2 instanceof AST.int_const && evalExpr(cls, finalExpr.e2, out, varNameStart, aMap, false).equals("0"))){
				out.println("br label %" + (varNameStart.value+6));
				varNameStart.value++;
				evalExpr(cls, divErr, out, varNameStart, aMap, needPointer);
				out.println("call void @exit(i32 1)\n");
				out.println("br label %" + (varNameStart.value++));
			}
			return needPointer ? divRes : writeLoad(out, varNameStart, divRes, "Int");
		}
		else if(expr instanceof AST.isvoid){
			AST.isvoid finalExpr = (AST.isvoid) expr;
			String e1 = evalExpr(cls, finalExpr.e1, out, varNameStart, aMap, true);
			out.println("%" + (varNameStart.value++) + " = icmp eq " + getType(finalExpr.e1.type) + "* " + e1 + ", null");
			return needPointer ? writeStore(out, "%" + (varNameStart.value-1), varNameStart, "Bool") : "%" + (varNameStart.value-1);
		}
		else if(expr instanceof AST.assign){
			AST.assign finalExpr = (AST.assign) expr;
			String right = "";
			int varNum = aMap.get(finalExpr.name);
			System.out.println("Assgn ");
			if(!cls.alist.get(finalExpr.name).typeid.equals(finalExpr.e1.type)){
				right = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, true);
				addCast(cls.alist.get(finalExpr.name).typeid, finalExpr.e1.type);
				out.println("call void @CAST_" + cls.alist.get(finalExpr.name).typeid + "_" + finalExpr.e1.type + "( " + getType(cls.alist.get(finalExpr.name).typeid) + "* %" + varNum + ", " + getTypePointer(finalExpr.e1.type) + " " + right + " )");
				return needPointer ? right : writeLoad(out, varNameStart, right, finalExpr.e1.type);
			}
			else{
				right = evalExpr(cls, finalExpr.e1,out,varNameStart, aMap, false);
				writeStore(out, right, "%"+varNum, finalExpr.type);
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
				val = evalExpr(cls, e, out, varNameStart, aMap, needPointer);
			}
			return val;
		}
		else if(expr instanceof AST.static_dispatch){
			AST.static_dispatch finalExpr = (AST.static_dispatch) expr;
			ClassPlus callingCls = classMap.get(finalExpr.typeid);
			AST.method callingMeth = callingCls.mlist.get(finalExpr.name);
			mthdsToWrite.put(callingMeth, callingCls);
			System.out.println("Stat dsi on " + callingCls.name + " on " + callingMeth.name);
			String callVarName = evalExpr(cls, finalExpr.caller, out, varNameStart, aMap,true);
			if(!finalExpr.typeid.equals(finalExpr.caller.type)){
				addCast(finalExpr.typeid, finalExpr.caller.type);
				String callCastVal = writeAlloc(out, varNameStart, getType(finalExpr.typeid));
				out.println("call void @CAST_" + finalExpr.typeid + "_" + finalExpr.caller.type + "( " + getType(finalExpr.typeid) + "* " + callCastVal + ", " + getTypePointer(finalExpr.caller.type) + " " + callVarName + " )");
				callVarName = callCastVal;
			}
			String retType = callingMeth.typeid.equals("SELF_TYPE") ? finalExpr.typeid : callingMeth.typeid;
			String callStr = ("call " + getType(retType) + (baseClasses.contains(finalExpr.typeid) ? "" : "*") + " @" + finalExpr.name + "_" + finalExpr.typeid + "(" + getTypePointer(callingCls.name) + " " + callVarName);// Add actuals and case of strings
			for(int i = 0; i < callingMeth.formals.size() ; ++i){
				String val = evalExpr(cls, finalExpr.actuals.get(i), out, varNameStart, aMap, true);
				if(!finalExpr.actuals.get(i).type.equals(callingMeth.formals.get(i).typeid)){
					addCast(callingMeth.formals.get(i).typeid, finalExpr.actuals.get(i).type);
					String castVal = writeAlloc(out, varNameStart, getType(callingMeth.formals.get(i).typeid));
					out.println("call void @CAST_" + callingMeth.formals.get(i).typeid + "_" + finalExpr.actuals.get(i).type + "( " + getType(callingMeth.formals.get(i).typeid) + "* " + castVal + ", " + getTypePointer(finalExpr.actuals.get(i).type) + " " + val + " )");
					val = castVal;
				}
				callStr += ", " + getType(callingMeth.formals.get(i).typeid) + "* " + val; 
			}
			out.println("%" + (varNameStart.value++) + " = " + callStr + ")");
			//return needPointer ? "%" + (varNameStart.value-1) : writeLoad(out, varNameStart, "%" + (varNameStart.value-1), callingMeth.typeid);
			if(needPointer){
				if(baseClasses.contains(retType)){
					return writeStore(out, "%" + (varNameStart.value-1), varNameStart, retType);
				}
				else{
					return "%" + (varNameStart.value-1);
				}
			}
			else{
				if(baseClasses.contains(retType)){
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
			String cond = evalExpr(cls, finalExpr.predicate, out, varNameStart, aMap, false);
			String finalVar = writeAlloc(out, varNameStart, needPointer ? getTypePointer(finalExpr.type) : getType(finalExpr.type));
			out.print("br i1 " + cond);
			int iflabel = varNameStart.value++;
			StringWriter buffStr = new StringWriter();
			PrintWriter buffer = new PrintWriter(buffStr);
			ifval = evalExpr(cls, finalExpr.ifbody, buffer, varNameStart, aMap, needPointer);
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
			buffer = new PrintWriter(buffStr);
			elseval = evalExpr(cls, finalExpr.elsebody, buffer, varNameStart, aMap, needPointer);
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
			String pred = evalExpr(cls, finalExpr.predicate, out, varNameStart, aMap, false);
			out.print("br i1 " + pred + ", label %" + (varNameStart.value++) + ", label %");

			StringWriter buffStr = new StringWriter();
			PrintWriter buffer = new PrintWriter(buffStr);
			evalExpr(cls, finalExpr.body, buffer, varNameStart, aMap, false);
			buffer.println("br label " + loopStrt);
			out.println(varNameStart.value);
			out.println(buffStr.toString());
			varNameStart.value++;
			String retVar = "%" + (varNameStart.value++);
			out.println(retVar + " = call %class.Object* @createVoid()");
			return needPointer ? retVar : writeLoad(out, varNameStart, retVar, "Object");
		}
		System.out.println("Unchecked case");
		return "0";
	}
	private void checkExpr(){
		
	}






	class IntPointer{
		public int value;
		public IntPointer(){value = 0;}
		public IntPointer(int initValue){value = initValue;}
	}
	private void addCast(String a, String b){
		if(castToWrite.containsKey(a)) castToWrite.get(a).add(b);
		else castToWrite.put(a, new HashSet<String>(Arrays.asList(b)));
	}
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
					q.add(c.name);
				}
			}
		}
	}
}
