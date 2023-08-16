package retromachines;

import it.unimi.dsi.fastutil.bytes.ByteArrayPriorityQueue;
import it.unimi.dsi.fastutil.bytes.BytePriorityQueue;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import retromachines.rboy.RBoyEvents;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class GameboySound extends AbstractSoundInstance {
	public GameboySound(Vec3d pos) {
		super(new Identifier("retromachines", "gameboy"), SoundCategory.RECORDS, SoundInstance.createRandom());
		x = pos.x;
		y = pos.y;
		z = pos.z;
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
		return CompletableFuture.completedFuture(new GameboySoundStream());
	}

	public static class GameboySoundStream implements AudioStream {
		private static final AudioFormat FORMAT = new AudioFormat(44100, 8, 1, false, false);

		private static final BytePriorityQueue leftQueue = new ByteArrayPriorityQueue();
		private static final BytePriorityQueue rightQueue = new ByteArrayPriorityQueue();

		static {
			// Maybe need to alter jni.rs underflowed?
			RBoyEvents.AUDIO_DATA.register((leftChannel, rightChannel) -> {
				synchronized (GameboySoundStream.class) {
					for (float v : leftChannel) {
						if (leftQueue.size() >= FORMAT.getSampleRate()) {
							// Make sure we dont get a massive buffer, only store 1 second of data at most
							break;
						}
						leftQueue.enqueue((byte) (v * 255));
					}

					for (float v : rightChannel) {
						if (rightQueue.size() >= FORMAT.getSampleRate()) {
							break;
						}
						rightQueue.enqueue((byte) (v * 255));
					}
				}
			});
		}

		@Override
		public AudioFormat getFormat() {
			return FORMAT;
		}

		@Override
		public ByteBuffer getBuffer(int size) {
			// Not really too sure what to do here, see: https://github.com/mvdnes/rboy/blob/master/src/main.rs#L454C52-L454C52
			synchronized (GameboySoundStream.class) {
				ByteBuffer outBuffer = BufferUtils.createByteBuffer(size);

				assert leftQueue.size() == rightQueue.size();

				int outLen = Math.min(size / 2, leftQueue.size());

				for (int i = 0; i < outLen; i++) {
					// each sample needs to be 8 bits (2 bytes)?
					outBuffer.put(leftQueue.dequeueByte());
					outBuffer.put(rightQueue.dequeueByte()); // Doesnt seem to stop if I comment this out
				}

				System.out.println(leftQueue.size());

				return outBuffer;
			}
		}

		@Override
		public void close() throws IOException {
			System.out.println("close");
		}
	}
}
