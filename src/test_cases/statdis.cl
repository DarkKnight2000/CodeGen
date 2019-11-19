class Main {
	a:Int;
	b:IO <- new IO;
	c:A <- new A;
	d:B <- new B;
    e:A;
	main():Int{{
		d@A.f();
		d@B.g(e,c);
		e;
		b@IO.out_int(e@A.ret());
		c@A.abort();
		a;
	}};
	
};

class A{
	a:Int <- 1;
	b:A;
	f():Int{{
		new A@A.abort();
		a;
	}};
	-- Predefined fns can be overrided.
	abort():Object{
		new Object
	};
	ret():Int{
		a
	};
};

class B inherits A{
	c:B;

	g(a:A, b:A):B{{
		a <- b;
		new B;
	}};
};