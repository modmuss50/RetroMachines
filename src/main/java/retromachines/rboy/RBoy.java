package retromachines.rboy;

// javac -h . RBoy.java
public class RBoy {
	public static class Events {
		public static final int KEY_A_DOWN = 1;
		public static final int KEY_B_DOWN = 2;
		public static final int KEY_UP_DOWN = 3;
		public static final int KEY_DOWN_DOWN = 4;
		public static final int KEY_LEFT_DOWN = 5;
		public static final int KEY_RIGHT_DOWN = 6;
		public static final int KEY_SELECT_DOWN = 7;
		public static final int KEY_START_DOWN = 8;
		public static final int KEY_A_UP = 9;
		public static final int KEY_B_UP = 10;
		public static final int KEY_UP_UP = 11;
		public static final int KEY_DOWN_UP = 12;
		public static final int KEY_LEFT_UP = 13;
		public static final int KEY_RIGHT_UP = 14;
		public static final int KEY_SELECT_UP = 15;
		public static final int KEY_START_UP = 16;

		public static final int STOP = 101;
		public static final int SPEED_UP = 102;
		public static final int SPEED_DOWN = 103;
	}

	public static native long construct_cpu(byte[] romData, boolean useNativeAudio);

	public static native void run_cpu(long contextPtr);

	public static native byte[] get_gpu_data(long contextPtr);

	public static native void send_event(long contextPtr, int event);

	public record Context(long ptr) {
		public static Context create(byte[] romData, boolean useNativeAudio) {
			long ptr = construct_cpu(romData, useNativeAudio);

			if (ptr == 0) {
				throw new RuntimeException();
			}

			return new Context(ptr);
		}

		public void runCpu() {
			run_cpu(ptr);
		}

		public byte[] getGpuData() {
			return get_gpu_data(ptr);
		}
		
		public void sendEvent(int event) {
			send_event(ptr, event);
		}
	}
}
