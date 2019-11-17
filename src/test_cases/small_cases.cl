class Main {
	i:Int <- a+a;
	j:Int <- ~a;
	a:Int <- s1@String.length();
	a2:IO <- new IO@IO.out_string("J");
	b:Object;
	c:C;
	a1:A <- c <- new C;
	s:String <- "Hi";
	s1:String <- new String;
	bo:Bool <- not true;
	bo1:Bool <- i <= j;
	main(a: String):Int{
		0
	};
	s():String{
		new String
	};
};

class A{
	a: Int;
};
class B inherits A{
	b: Int;
};

class C inherits B{
	c: Int;
};