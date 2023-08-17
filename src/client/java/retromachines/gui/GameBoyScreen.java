package retromachines.gui;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import retromachines.GameBoyRom;
import retromachines.GameboySound;
import retromachines.rboy.RBoy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class GameBoyScreen extends Screen {
	private static final Identifier BACKGROUND_TEXTURE = new Identifier("retromachines", "textures/gui/gameboy.png");
	private static final Identifier GPU_TEXTURE = new Identifier("retromachines", "gpu");
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final int backgroundWidth = 256;
	private static final int backgroundHeight = 409;
	private static final int screenWidth = 160;
	private static final int screenHeight = 144;

	/**
	 * Disable to process the audio within java
	 */
	private static final boolean USE_NATIVE_AUDIO = true;

	@Nullable
	private Gameboy gameboy;

	private IntSet pressedKeys = new IntOpenHashSet();
	private boolean selectingRom = false;
	private AtomicReference<String> selectedPath = new AtomicReference<>();

	public GameBoyScreen(GameBoyRom rom) {
		super(Text.literal("Gameboy"));

		gameboy = new Gameboy(rom);

		// TODO do we need to close this?
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
		textureManager.registerTexture(GPU_TEXTURE, new GPUTexture());

		if (!USE_NATIVE_AUDIO) {
			MinecraftClient client = MinecraftClient.getInstance();
			client.getSoundManager().play(new GameboySound(client.player.getPos()));
		}
	}

	@Override
	protected void init() {
		super.init();
	}

	@Override
	public void close() {
		if (gameboy != null) {
			try {
				gameboy.close();
				gameboy = null;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		super.close();
	}

	@Override
	public void tick() {
		super.tick();

		final String path = selectedPath.getAndSet(null);

		if (path == null) {
			return;
		}

		Path rom = Paths.get(path);

		if (Files.notExists(rom)) {
			LOGGER.error("Failed to load ROM from {}", path);
			return;
		}

		if (gameboy != null) {
			try {
				gameboy.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		try {
			gameboy = new Gameboy(new GameBoyRom.LocalRom(rom));
		} catch (Throwable e) {
			e.printStackTrace();

			MinecraftClient.getInstance().getToastManager().add(new SystemToast(SystemToast.Type.PACK_LOAD_FAILURE, Text.literal("Failed to load ROM"), null));
			// Failed to load the custom rom.
			gameboy = null;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int x = (this.width - backgroundWidth) / 2;
		int y = (this.height - backgroundHeight) / 2;
		boolean needsToScale = backgroundHeight > this.height;

		renderBackground(context); // Tint

		context.getMatrices().push();

		// A cheap way to scale it to fit smaller screens.
		if (needsToScale) {
			context.getMatrices().scale(0.5F, 0.5F, 0);
			context.getMatrices().translate(this.width / 2, this.height/ 2, 0);
		}

		context.drawTexture(BACKGROUND_TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, 512);

		for (int pressedKey : pressedKeys) {
			switch (pressedKey) {
				case RBoy.Events.KEY_UP_DOWN -> {
					context.drawTexture(BACKGROUND_TEXTURE, x + 47, y + 242, 0, 410, 24, 24, backgroundWidth, 512);
				}
				case RBoy.Events.KEY_DOWN_DOWN -> {
					context.drawTexture(BACKGROUND_TEXTURE, x + 47, y + 290, 0, 410, 24, 24, backgroundWidth, 512);
				}
				case RBoy.Events.KEY_LEFT_DOWN -> {
					context.drawTexture(BACKGROUND_TEXTURE, x + 23, y + 266, 0, 410, 24, 24, backgroundWidth, 512);
				}
				case RBoy.Events.KEY_RIGHT_DOWN -> {
					context.drawTexture(BACKGROUND_TEXTURE, x + 71, y + 266, 0, 410, 24, 24, backgroundWidth, 512);
				}
				default -> {}
			}
		}

		boolean hoveringOpenButton = hoveringOpenButton(mouseX, mouseY);
		if (hoveringOpenButton) {
			context.drawTexture(BACKGROUND_TEXTURE, x + 25, y + 363, 24, 410, 26, 22, backgroundWidth, 512);
		}

		if (gameboy == null) {
			return;
		}

		byte[] gpuData = gameboy.getContext().getGpuData();

		MinecraftClient.getInstance().getTextureManager().bindTexture(GPU_TEXTURE);

		ByteBuffer buffer = BufferUtils.createByteBuffer(gpuData.length);
		buffer.put(gpuData).flip();

		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_ZERO);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_ZERO);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_ZERO);

		GL11.glTexImage2D(
			GL11.GL_TEXTURE_2D,
			0,
			GL30.GL_RGB8, screenWidth, screenHeight,
			0,
			GL11.GL_RGB,
			GL11.GL_UNSIGNED_BYTE,
			buffer);

		context.drawTexture(
			GPU_TEXTURE,
			x + 48, y + 48,
			0, 0, screenWidth, screenHeight, screenWidth, screenHeight
		);

		context.getMatrices().pop();

		if (hoveringOpenButton) {
			context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.literal("Open ROM"), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hoveringOpenButton(mouseX, mouseY)) {
			selectRom();
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean hoveringOpenButton(double mouseX, double mouseY) {
		// Dont ask, I dont know.
		int x = (this.width - backgroundWidth) / 2;
		int y = (this.height - backgroundHeight) / 2;
		int mx = (int) (backgroundHeight > this.height ? (mouseX - width / 2 + backgroundWidth / 4) * 2 : mouseX - x);
		int my = (int) (backgroundHeight > this.height ? (mouseY - height / 2 + backgroundHeight / 4) * 2 : mouseY - y);

		return mx >= 26 && mx <= 50
			&& my >= 364 && my <= 384;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		KeyMap keyMap = KeyMap.get(keyCode);

		if (keyMap != null && gameboy != null) {
			pressedKeys.add(keyMap.keyDownEvent);
			gameboy.getContext().sendEvent(keyMap.keyDownEvent);
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		KeyMap keyMap = KeyMap.get(keyCode);

		if (keyMap != null && gameboy != null) {
			pressedKeys.remove(keyMap.keyDownEvent);
			gameboy.getContext().sendEvent(keyMap.keyUpEvent);
			return true;
		}

		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void filesDragged(List<Path> paths) {
		if (paths.size() != 1) {
			return;
		}

		selectedPath.set(paths.get(0).toAbsolutePath().toString());
	}

	private void selectRom() {
		if (selectingRom) {
			return;
		}

		selectingRom = true;

		CompletableFuture.supplyAsync(() -> TinyFileDialogs.tinyfd_openFileDialog(
			"Select ROM",
			"",
			null,
			"Gameboy ROM",
			false
		)).whenComplete((path, throwable) -> {
			if (throwable != null) {
				throwable.printStackTrace();
				return;
			}

			selectingRom = false;

			if (path == null) {
				// Nothing selected
				return;
			}

			selectedPath.set(path);
		});
	}

	private static class Gameboy implements AutoCloseable {
		private final RBoy.Context context;
		private final Thread gameboyThread;

		public Gameboy(GameBoyRom rom) {
			try {
				context = RBoy.Context.create(rom.loadRom(), USE_NATIVE_AUDIO);
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to load gameboy rom", e);
			}

			gameboyThread = new Thread(context::runCpu);
			gameboyThread.setName("RetroMachines: Gameboy");
			gameboyThread.setDaemon(true);
			gameboyThread.start();
		}

		public RBoy.Context getContext() {
			return context;
		}

		@Override
		public void close() throws Exception {
			context.sendEvent(RBoy.Events.STOP);
			try {
				gameboyThread.join(Duration.ofSeconds(10).toMillis());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	enum KeyMap {
		A(GLFW.GLFW_KEY_Z, RBoy.Events.KEY_A_DOWN, RBoy.Events.KEY_A_UP),
		B(GLFW.GLFW_KEY_X, RBoy.Events.KEY_B_DOWN, RBoy.Events.KEY_B_UP),
		UP(GLFW.GLFW_KEY_UP, RBoy.Events.KEY_UP_DOWN, RBoy.Events.KEY_UP_UP),
		DOWN(GLFW.GLFW_KEY_DOWN, RBoy.Events.KEY_DOWN_DOWN, RBoy.Events.KEY_DOWN_UP),
		LEFT(GLFW.GLFW_KEY_LEFT, RBoy.Events.KEY_LEFT_DOWN, RBoy.Events.KEY_LEFT_UP),
		RIGHT(GLFW.GLFW_KEY_RIGHT, RBoy.Events.KEY_RIGHT_DOWN, RBoy.Events.KEY_RIGHT_UP),
		SELECT(GLFW.GLFW_KEY_SPACE, RBoy.Events.KEY_SELECT_DOWN, RBoy.Events.KEY_SELECT_UP),
		START(GLFW.GLFW_KEY_ENTER, RBoy.Events.KEY_START_DOWN, RBoy.Events.KEY_START_UP)
		;

		@MagicConstant(valuesFromClass = GLFW.class)
		final int keyCode;
		@MagicConstant(valuesFromClass = RBoy.Events.class)
		final int keyDownEvent;
		@MagicConstant(valuesFromClass = RBoy.Events.class)
		final int keyUpEvent;

		KeyMap(@MagicConstant(valuesFromClass = GLFW.class) int keyCode, @MagicConstant(valuesFromClass = RBoy.Events.class) int keyDownEvent, @MagicConstant(valuesFromClass = RBoy.Events.class) int keyUpEvent) {
			this.keyCode = keyCode;
			this.keyDownEvent = keyDownEvent;
			this.keyUpEvent = keyUpEvent;
		}

		@Nullable
		public static KeyMap get(@MagicConstant(valuesFromClass = GLFW.class) int keyCode) {
			for (KeyMap value : values()) {
				if (value.keyCode == keyCode) {
					return value;
				}
			}

			return null;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static class GPUTexture extends AbstractTexture {
		@Override
		public void load(ResourceManager manager) throws IOException {
		}
	}
}
