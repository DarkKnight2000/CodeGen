-- All functions have a pointer to their containing class as a extra parameter
-- For main method this pointer passed should call constructor of Main class as it is the starting the pointer
-- of execution
class Main {
	a:Int;
	b:IO <- new IO;
	c:A;
	main():Int{{
		c@A.f();
		if isvoid(b) then new IO@IO.out_string("isvoid\n") else new IO@IO.out_string("not void\n") fi;
		a/a;
	}};
	
};

class A{
	a:Int;
	b:A;
	f():Int{{
		new A@A.abort();
		a;
	}};
	-- Predefined fns can be overrided.
	abort():Object{
		new Object
	};
};