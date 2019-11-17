class Main {
	a:IO <- new IO@IO.out_string(("a")@String.concat("b"));
	main():IO{{
		new Main;
		new IO;
	}};
};