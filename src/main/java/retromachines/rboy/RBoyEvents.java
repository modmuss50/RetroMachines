package retromachines.rboy;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class RBoyEvents {
	public static Event<AudioData> AUDIO_DATA = EventFactory.createArrayBacked(AudioData.class, callbacks -> (leftChannel, rightChannel) -> {
		for (AudioData callback : callbacks) {
			callback.onData(leftChannel, rightChannel);
		}
	});

	// Invoked via JNI
	@SuppressWarnings("unused")
	public static void audio_callback(float[] leftChannel, float[] rightChannel) {
		try {
			AUDIO_DATA.invoker().onData(leftChannel, rightChannel);
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		}
	}

	public interface AudioData {
		void onData(float[] leftChannel, float[] rightChannel);
	}
}
