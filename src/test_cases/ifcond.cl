class Main {
	b:IO <- new IO@IO.out_string("Start\n");
	main(a: String):IO{
		(if true then new IO else new IO fi)@IO.out_string("Done\n")
	};
};