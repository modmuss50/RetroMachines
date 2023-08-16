package retromachines.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import retromachines.GameboySound;
import retromachines.rboy.RBoy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;

public class GameBoyScreen extends Screen {
	private static final Identifier BACKGROUND_TEXTURE = new Identifier("retromachines", "textures/gui/gameboy.png");
	private static final Identifier GPU_TEXTURE = new Identifier("retromachines", "gpu");

	/**
	 * Disable to process the audio within java
	 */
	private static final boolean USE_NATIVE_AUDIO = true;

	private final RBoy.Context gameboy;
	private final Thread gameboyThread;

	public GameBoyScreen() {
		super(Text.literal("Gameboy"));

		gameboy = RBoy.Context.create("/Users/mark/Downloads/Tetris/Tetris.gb", USE_NATIVE_AUDIO);

		gameboyThread = new Thread(gameboy::runCpu);
		gameboyThread.setName("RetroMachines: Gameboy");
		gameboyThread.setDaemon(true);
		gameboyThread.start();

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
		gameboy.sendEvent(RBoy.Events.STOP);
		try {
			gameboyThread.join(Duration.ofSeconds(10).toMillis());
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		super.close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int backgroundWidth = 256;
		int backgroundHeight = 409;

		int x = (this.width - backgroundWidth) / 2;
		int y = (this.height - backgroundHeight) / 2;

		context.drawTexture(BACKGROUND_TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, 512);

		int width = 160;
		int height = 144;

		byte[] gpuData = gameboy.getGpuData();

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
			GL30.GL_RGB8,
			width,
			height,
			0,
			GL11.GL_RGB,
			GL11.GL_UNSIGNED_BYTE,
			buffer);

		context.drawTexture(
			GPU_TEXTURE,
			x + 48, y + 48,
			0, 0,
			width, height,
			width, height
		);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		KeyMap keyMap = KeyMap.get(keyCode);

		if (keyMap != null) {
			gameboy.sendEvent(keyMap.keyDownEvent);
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		KeyMap keyMap = KeyMap.get(keyCode);

		if (keyMap != null) {
			gameboy.sendEvent(keyMap.keyUpEvent);
			return true;
		}

		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private enum KeyMap {
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
		private final int keyCode;
		@MagicConstant(valuesFromClass = RBoy.Events.class)
		private final int keyDownEvent;
		@MagicConstant(valuesFromClass = RBoy.Events.class)
		private final int keyUpEvent;

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
