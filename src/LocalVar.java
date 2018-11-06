
public class LocalVar {

	static int foo1() {
		int a = 2; // 声明常量a
		int b = 3; // 声明常量b
		return a + b; // 常量表达式
	}

	static int foo2() { // 常量折叠
		final int a = 2; // 声明常量a
		final int b = 3; // 声明常量b
		return a + b; // 常量表达式
	}

}

/*
  static int foo1();
    Code:
       0: iconst_2
       1: istore_0
       2: iconst_3
       3: istore_1
       4: iload_0
       5: iload_1
       6: iadd
       7: ireturn

  static int foo2();
    Code:
       0: iconst_2
       1: istore_0
       2: iconst_3
       3: istore_1
       4: iconst_5
       5: ireturn
*/

/* Preferences->Java->Compiler->Code Generation->Preserve unused (never read) local variables把钩去掉
  static int foo1();
    Code:
       0: iconst_2
       1: istore_0
       2: iconst_3
       3: istore_1
       4: iload_0
       5: iload_1
       6: iadd
       7: ireturn

  static int foo2();
    Code:
       0: iconst_5
       1: ireturn
*/