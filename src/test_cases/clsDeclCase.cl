-- This case shows that attrs with non datatype classes shld be stored as
-- pointers to objects of original classes in ir of containing class.

class Main {
	a:Int <- b+1;
	b:Int <- 1;
	main():Int{{
		new IO@IO.out_int(a);
		0;
	}};	
};

class C{
	a: Int;
};
class B inherits C{
	b: Int;
};

class A inherits B{
	c: Int;
	a1:B <- new A;
};