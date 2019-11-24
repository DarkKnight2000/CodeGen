# LLVM-IR code generator for cool language

* We start by processing the program and creating a ClassPlus object for each class

* A hashmap of initOrder is maintained wich keeps track of order of declaration of attributes in each class based on the lineNo property in them.
    The initialisations in the constructor are done in this order

* All class declarations are emitted into IR

* Only methods which will be called directly or indirectly from main are emitted

* We find all these methods by keeping two buffers. We loop over one buffer and collect methods that
    are called from each method in the 1st buffer and store them in 2nd buffer. After the loop we move 2nd buffer contents
    to 1st buffer. All this is done in a while loop. This loop ends when both buffers are empty. The 1st buffer is initialised with "main" method.

* writeClass method emits the declaration( including inherited attributes ). It is stored with the name %class.{class.name}

* writeConstructor emits the constructor for a class i.e., writes the values of attributes in their respective pointers.

* writeMethod emits the method declaration and its formals. All the formals are pointers to the objects.

* writeMthdBody emits the method body.

* In the start of main method, we created a pointer for Main class and called constructor on it. As this is the starting point of program it needs a pointer to work as 'this' inside the body.

* The function evalExpr is the main part of the code. It is used to write the evaluation of a expression into IR. It is called recursively on the expressions inside a expression to evaluate them.
    ** The if conditional is handles by creating another printwriter which stores the emitted ir of ifbody and elsebody. Then we calculate the branch values accordingly
        and emit them into file from the buffers. This is similar for the while loop.
    ** AST.object is handled by creating a HashMap to store the variable number in which a attr/formal is stored in, at the start of constructor/method. The corresponding variable number is returned from the name of the object.
    ** AST.isvoid uses a compare instruction between the pointer and null.
    ** In AST.static_dispatch there is a chance that the actual might be from a child class of the formal. In that case we called the cast function and used the resulting pointer.There is a if-condition at the beginning to
        check for dispatch on void. There is an exit instruction at the end, so if there is an error the program execution stops after printing out the error. We excluded checking this check if caller is AST.new_ as this cannot be void 
    ** AST.divide is also evaluated in the same way. 1st both operands(/expressions) are evaluated. Then there is a if-condition to check if denominator is 0. To report error we used a expression variable 'divErr' with all types set. evalExpr function is called on it to emit it into IR.
        The error is reported by a static dispatch on a new IO object, so there is no check for void in it again there again.

* evalExpr function also takes a boolean parameter. This boolean can be set to true, if we want the pointer to the value of the expression being evaluated( in case of static_dispatch where we need pointers to the actual expressions )
, or it can be false if we just need the value( in case of plus we just need values of both operands to add them ).

* Lets call Int, String, Bool as basic types as they dont need separate declarations in IR.

* We have taken care that for non-basic types, the instruction with pointer to pointer( which is essentially a reference to the object ) is just before instruction containing pointer of that variable. This property is used in handling static_dispatch to get the pointer to pointer.

* We also keep a HashMap(attrNumMap) from attr.name to the offset we need for gettting the pointer from getelementptr. This can be used to write the cast method where we store values of inherited attributes from child pointer in parent pointer ( which is passed as argument )

* In the cast method, we pass a parent class pointer in which we store the result of casting. We loop over attributes of parent/ancestor class, get the respective offset for getelementptr and store the value in the parent class.

* All arguments of a method are pointers. All updates inside the body should be done on copies. But basic classes cant be updated in a method call i.e, they are passed as values. It is the same for user-defined classes.
    When we say pointer to a user-defined class we refer to a pointer to a pointer of the object.
    ** For basic classes we create a pointer, load the value from the passed parameter, and store it in this variable. So any changes to this pointer doesnt effect the value in original one.
    ** But for non-basic classes, the value is already a pointer. So we created a pointer to pointer and used this further. This wont make a difference until we start changing the values in the pointer.
        But the methods are written so that they work only on copies, so it wouldn't cause a problem.
    ** Another problem which made us create a pointer to pointer is AST.assign. There can be cases where there is an assignment inside the body, for non-basic classes( which are pointers ) this means the value in pointer to their pointer is changed to the pointer being assigned.
    As this is not the actual pointer to the value( a pointer of user-defined class ) being passed, it doesnt affect original value.

* In the provided semantic the argument of isvoid was not assigned type. We updated it accordingly.