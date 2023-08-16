package retromachines;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import retromachines.gui.GameBoyScreen;

public class RetroMachinesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);

			if (!world.isClient) {
				return TypedActionResult.pass(stack);
			}

			if (stack.getItem() == RetroMachines.GAME_BOY_ITEM) {
				MinecraftClient.getInstance().setScreen(new GameBoyScreen());
				return TypedActionResult.success(stack);
			}

			return TypedActionResult.pass(stack);
		});
	}
}