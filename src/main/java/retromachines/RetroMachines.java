package retromachines;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import retromachines.item.GameBoyItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class RetroMachines implements ModInitializer {
	public static final GameBoyItem GAME_BOY_ITEM = new GameBoyItem();

	@Override
	public void onInitialize() {
		try {
			System.load(extractNatives());
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to extract natives", e);
		}

		Registry.register(Registries.ITEM, new Identifier("retromachines", "gameboy"), GAME_BOY_ITEM);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(GAME_BOY_ITEM);
		});
	}

	private static String extractNatives() throws IOException {
		Path modDir = FabricLoader.getInstance().getGameDir().resolve(".retromachines");
		String fileName = Architecture.current().prefix + "-" + OperatingSystem.current().suffix;
		Path nativesPath = modDir.resolve(fileName);

		Files.createDirectories(nativesPath.getParent());

		try (InputStream inputStream = RetroMachines.class.getClassLoader().getResourceAsStream("natives/" + fileName)) {
			if (inputStream == null) {
				throw new UnsupportedOperationException("Retro machines is not compatible with the current platform: " + fileName);
			}

			Files.copy(inputStream, modDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
		}

		return nativesPath.toAbsolutePath().toString();
	}

	// https://rust-lang.github.io/rustup-components-history/
	enum Architecture {
		X64("x86_64"),
		ARM64("aarch64"),
		I386("i686");
		private final String prefix;

		Architecture(String prefix) {
			this.prefix = prefix;
		}

		static Architecture current() {
			final String arch = System.getProperty("os.arch");

			if ("aarch64".equals(arch) || arch.startsWith("armv8")) {
				return ARM64;
			} else if (arch.contains("64")) {
				return X64;
			}

			return I386; // Default
		}
	}

	enum OperatingSystem {
		MAC_OS("apple-darwin/librboy.dylib"),
		LINUX("unknown-linux-gnu/librboy.so"),
		WINDOWS("pc-windows-msvc/rboy.dll");
		private final String suffix;


		OperatingSystem(String suffix) {
			this.suffix = suffix;
		}

		static OperatingSystem current() {
			final String osName = System.getProperty("os.name").toLowerCase();

			if (osName.contains("win")) {
				return WINDOWS;
			} else if (osName.contains("mac")) {
				return MAC_OS;
			} else {
				return LINUX; // Also default
			}
		}
	}
}