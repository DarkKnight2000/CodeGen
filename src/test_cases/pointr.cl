class Main {
	a:Int;
	b:IO <- new IO;
	c:A <- new A;
	d:B <- new B;
    e:A;
	main():Int{{
        -- Checking if casting is working
		d@A.f();
        -- CHecking what happens when a null is passed to
        -- static dispatch
		d@B.g(e,c,a);
		e;
		b@IO.out_int(a);
		d@B.h(a);
		b@IO.out_int(a);
        -- CHecking if attribute 'e' value has changed
		b@IO.out_int(e@A.ret());
        -- Checking for overriding is working
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
        self@A.ret();
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

	g(a:A, b:A, c:Int):B{{
		a <- b;
		c <- 3;
		new B;
	}};
	h(a:Int):Int{
        -- Checking if changing Int value in fn.
        -- changes value in passed variable
		a <- 2
	};
};