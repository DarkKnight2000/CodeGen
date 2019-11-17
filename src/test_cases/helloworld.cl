class Main {
	a:Int <- b;
	b:Int <- a;
	main():Int{{
		new IO@IO.out_int(a);
		0;
	}};	
};