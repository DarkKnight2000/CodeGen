class Main {
	s1:String <- new IO@IO.in_string();
	a:Int <- s1@String.length();
	a1:Int <- new IO@IO.in_int();
	a2:IO <- new IO@IO.out_int(a)@IO.out_string("-x-\n");
	main(a: String):IO{{
		new Main;
		new IO@IO.out_int(1)@IO.out_string("\n");
	}};
};