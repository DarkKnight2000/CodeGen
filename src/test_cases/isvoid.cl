-- isvoid is not working

class Main {
	a:IO;
	main():IO{{
		while isvoid(a) loop new IO@IO.out_string("a\n") pool;
		a@IO.out_string(new IO@Object.type_name());
	}};
};