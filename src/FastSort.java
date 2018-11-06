
public class FastSort {
	
	public static void main(String[] args) {
//		int[] array = {9,3,8,1,7,31,10,64,2,74,23,875,34};
		int[] array = {5,1,7,2,8,4,3,8,9,3,0,5,3,1,6,8,10,2,11};
		sort(array, 0, array.length);
		print(array);
	}

	public static void sort(int[] array, int start, int end) {
		if (end - start >= 2) {
			int i = start;
			int j = end;
			int keyIndex = i;
			int key = array[keyIndex];
			int tmp = -1;
			boolean forward = false;
			for (int k = 0; k < array.length - 1; k++) {
				if (!forward) {
					if (array[--j] < key) {
						tmp = array[i];
						array[i] = array[j];
						array[j] = tmp;
						keyIndex = j;
						forward = !forward;
					}
				} else {
					if (array[++i] > key) {
						tmp = array[j];
						array[j] = array[i];
						array[i] = tmp;
						keyIndex = i;
						forward = !forward;
					}
				}
				if (i + 1 == j) {
					break;
				}
			}
//			print(array); // 输出每次排序结果
			sort(array, start, keyIndex); // 左
			sort(array, keyIndex + 1, end); // 右
		}
	}
	
	public static void print(int[] array) {
		for (int i = 0; i < array.length; i++) {
			System.out.print(array[i]);
			if (i != array.length - 1) {
				System.out.print(",");
			}
		}
		System.out.println();
	}
	
}
