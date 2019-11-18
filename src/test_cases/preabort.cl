-- Checking for abort
class Main {
	a:IO <- new IO@IO.out_string(new IO@IO.type_name());
	main():IO{{
		new IO@IO.abort();
		new Main;
		new IO;
	}};
};