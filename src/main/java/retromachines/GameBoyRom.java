package retromachines;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public interface GameBoyRom {
	GameBoyRom BUILTIN_2048 = new BuiltinRom("2048", "https://github.com/Sanqui/2048-gb", "roms/2048.gb");

	String name();

	String credit();

	byte[] loadRom() throws IOException;

	record BuiltinRom(String name, String credit, String filename) implements GameBoyRom {
		@Override
		public byte[] loadRom() throws IOException {
			try (InputStream is = GameBoyRom.class.getClassLoader().getResourceAsStream(filename)) {
				Objects.requireNonNull(is, "Could not find: " + filename);
				return is.readAllBytes();
			}
		}
	}
}
