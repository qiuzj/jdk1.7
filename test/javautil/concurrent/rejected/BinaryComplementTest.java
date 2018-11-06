package javautil.concurrent.rejected;

/**
 * 计算机以补码存储数据。
 * 正数：原码、反码、补码相同。
 * 负数：反码符号位不变化，其余位数取反；补码为反码+1。
 * 
 * @author qiuzj
 *
 */
public class BinaryComplementTest {
	public static void main(String[] args) {
		/*
		 * Java中的数据类型都是带符号的。byte为8位，1位符号，7位数值。
		 * 所以byte的取值范围为[11111111,01111111]，即[-127,127]。
		 * 但由于计算机为了简化设计，采用补码而非原码进行存储，对于-0(10000000)作为特殊情况进行处理，用来存储多一个数。
		 * 所以最小取值可以再-1，即byte的最小取值为-128，因而byte的取值范围实际上为[-128,127]。
		 */
		System.out.println(Byte.MIN_VALUE); // -128
		System.out.println(Byte.MAX_VALUE); // 127
		
		System.out.println(~1); // 非. ![00000001]补=[11111110]补=-[00000001]原-1=-2
		System.out.println(~2); // 非. ![00000010]补=[11111101]补=[11111100]反=[10000011]原=-3
		System.out.println(~-1); // 非. [10000001]原=[11111110]反=[11111111]补, ![11111111]补=[00000000]补=[00000000]原=0
	}
}
