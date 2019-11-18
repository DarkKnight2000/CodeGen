class Main {
	a:Int;
	b:IO <- new IO;
	c:A <- new A;
	d:B <- new B;
	main():Int{{
		d@A.f();
		c@A.abort();
		if isvoid(b) then new IO@IO.out_string("isvoid\n") else new IO@IO.out_string("not void\n") fi;
		b@IO.out_string("b is working\n");
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

class B inherits A{
	c:B;
};