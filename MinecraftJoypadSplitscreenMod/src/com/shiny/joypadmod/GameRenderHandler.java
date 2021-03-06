package com.shiny.joypadmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Controllers;

import com.shiny.joypadmod.ControllerSettings.JoyBindingEnum;
import com.shiny.joypadmod.helpers.LogHelper;
import com.shiny.joypadmod.helpers.McGuiHelper;
import com.shiny.joypadmod.helpers.McObfuscationHelper;
import com.shiny.joypadmod.helpers.ModVersionHelper;
import com.shiny.joypadmod.inputevent.ControllerBinding;
import com.shiny.joypadmod.inputevent.ControllerBinding.BindingOptions;
import com.shiny.joypadmod.inputevent.ControllerInputEvent;
import com.shiny.joypadmod.lwjglVirtualInput.VirtualMouse;
import com.shiny.joypadmod.minecraftExtensions.JoypadConfigMenu;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

public class GameRenderHandler
{
	private static Minecraft mc = Minecraft.getMinecraft();
	public static int reticalColor = 0xFFFFFFFF;
	// boolean to allow the original controls menu.
	// normally we override the controls menu when seen
	public static boolean allowOrigControlsMenu = false;
	private static long lastInGuiTick = 0;
	private static long lastInGameTick = 0;
	static long lastScrollTick = 0;

	public static List<ControllerBinding> preRenderGuiBucket = new ArrayList<ControllerBinding>();
	public static List<ControllerBinding> preRenderGameBucket = new ArrayList<ControllerBinding>();

	public static void HandlePreRender()
	{
		try
		{
			if (mc.currentScreen != null && !ControllerSettings.isSuspended())
			{

				if (mc.currentScreen instanceof GuiControls)
				{
					if (!allowOrigControlsMenu)
					{
						ReplaceControlScreen((GuiControls) mc.currentScreen);
					}
				}
				else if (!(mc.currentScreen instanceof JoypadConfigMenu))
				{
					allowOrigControlsMenu = false;
				}

				if (InGuiCheckNeeded())
				{
					// This call here re-points the mouse position that Minecraft picks
					// up to determine if it should do the Hover over button effect.
					VirtualMouse.setXY(JoypadMouse.getmcX(), JoypadMouse.getmcY());
					if (preRenderGuiBucket.size() > 0)
					{
						for (ControllerBinding b : preRenderGuiBucket)
						{
							b.wasPressed(true, true);
						}
						preRenderGuiBucket.clear();
					}
					HandleDragAndScrolling();
				}
			}

			if (InGameCheckNeeded())
			{
				for (ControllerBinding binding : ControllerSettings.getGameAutoHandleBindings())
				{
					if (binding.bindingOptions.contains(BindingOptions.RENDER_TICK))
						binding.isPressed();
				}

				if (preRenderGameBucket.size() > 0)
				{
					for (ControllerBinding b : preRenderGameBucket)
					{
						b.wasPressed(true, true);
					}
					preRenderGameBucket.clear();
				}
			}
		}
		catch (Exception ex)
		{
			LogHelper.Fatal("Joypad mod unhandled exception caught! " + ex.toString());
		}
	}

	public static void HandlePostRender()
	{
		if (ControllerSettings.isSuspended())
			return;

		try
		{
			if (InGuiCheckNeeded())
			{
				// fixes issue with transitioning from inGui to game movement continuing
				if (Minecraft.getSystemTime() - lastInGameTick < 50)
				{
					ControllerSettings.unpressAll();
					Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = false;
				}

				DrawRetical();
			}

			if (InGameCheckNeeded())
			{
				// fixes issue with transitioning from inGame to Gui movement continuing
				if (Minecraft.getSystemTime() - lastInGuiTick < 50)
				{
					ControllerSettings.unpressAll();
					Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = false;
				}

				for (ControllerBinding binding : ControllerSettings.getGameAutoHandleBindings())
				{
					binding.isPressed();
				}

				UpdateInGameCamera();
			}
		}
		catch (Exception ex)
		{
			LogHelper.Fatal("Joypad mod unhandled exception caught! " + ex.toString());
		}

	}

	public static void HandleClientStartTick()
	{

		if (ControllerSettings.isSuspended())
			return;

		if (InGameCheckNeeded())
		{
			HandleJoystickInGame();
			lastInGameTick = Minecraft.getSystemTime();
		}

		if (InGuiCheckNeeded())
		{
			HandleJoystickInGui();
			lastInGuiTick = Minecraft.getSystemTime();
		}
	}

	public static void HandleClientEndTick()
	{
		// does nothing currently
	}

	private static void DrawRetical()
	{

		if (mc.currentScreen == null || !JoypadMod.controllerSettings.isInputEnabled())
			return;

		JoypadMouse.updateXY();
		int x = JoypadMouse.getX();
		int y = JoypadMouse.getY();

		Gui.drawRect(x - 3, y, x + 4, y + 1, reticalColor);
		Gui.drawRect(x, y - 3, x + 1, y + 4, reticalColor);
	}

	private static void UpdateInGameCamera()
	{
		JoypadMouse.updateXY();
		mc.thePlayer.setAngles(JoypadMouse.AxisReader.deltaX,
				JoypadMouse.AxisReader.deltaY * (ControllerSettings.getInvertYAxis() ? 1.0f : -1.0f));
	}

	private static void HandleDragAndScrolling()
	{

		if (VirtualMouse.isButtonDown(0) || VirtualMouse.isButtonDown(1))
		{
			// VirtualMouse.moveMouse(JoypadMouse.getmcX(), JoypadMouse.getmcY());
			McGuiHelper.guiMouseDrag(JoypadMouse.getX(), JoypadMouse.getY());
			VirtualMouse.setMouseButton(JoypadMouse.isLeftButtonDown() ? 0 : 1, true);
		}

		ControllerSettings.get(JoyBindingEnum.joyGuiScrollDown).isPressed();
		ControllerSettings.get(JoyBindingEnum.joyGuiScrollUp).isPressed();
	}

	private static void HandleJoystickInGui()
	{
		// update mouse coordinates
		// JoypadMouse.updateXY();
		VirtualMouse.setXY(JoypadMouse.getmcX(), JoypadMouse.getmcY());

		for (ControllerBinding binding : ControllerSettings.getMenuAutoHandleBindings())
		{
			if (!binding.bindingOptions.contains(BindingOptions.RENDER_TICK))
				binding.isPressed();
		}

		while (Controllers.next() && mc.currentScreen != null)
		{
			// ignore controller events in the milliseconds following in GAME
			// controlling
			if (Minecraft.getSystemTime() - lastInGameTick < 200)
				continue;

			if (ControllerSettings.loggingLevel > 3)
			{
				try
				{
					ControllerInputEvent inputEvent = ControllerSettings.controllerUtils.getLastEvent(
							ControllerSettings.joystick, Controllers.getEventControlIndex());
					if (inputEvent != null)
					{
						LogHelper.Info("Input event " + inputEvent.toString()
								+ " triggered.  Finding associated binding");
					}
				}
				catch (Exception ex)
				{
					LogHelper.Error("Exception caught debugging controller input events: " + ex.toString());
				}
			}

			for (ControllerBinding binding : ControllerSettings.getMenuAutoHandleBindings())
			{
				if (binding.bindingOptions.contains(BindingOptions.RENDER_TICK))
				{
					if (binding.wasPressed(false))
					{
						preRenderGuiBucket.add(binding);
						break;
					}
				}
				else if (binding.wasPressed())
					break;
			}
		}
	}

	private static void HandleJoystickInGame()
	{
		for (ControllerBinding binding : ControllerSettings.getGameAutoHandleBindings())
		{
			binding.isPressed();
		}

		while (Controllers.next() && mc.currentScreen == null)
		{
			// ignore controller events in the milliseconds following in GUI
			// controlling
			if (Minecraft.getSystemTime() - lastInGuiTick < 100)
				continue;

			if (ControllerSettings.loggingLevel > 3)
			{
				try
				{
					ControllerInputEvent inputEvent = ControllerSettings.controllerUtils.getLastEvent(
							ControllerSettings.joystick, Controllers.getEventControlIndex());
					if (inputEvent != null)
					{
						LogHelper.Info("Input event " + inputEvent.toString()
								+ " triggered.  Finding associated binding");
					}
				}
				catch (Exception ex)
				{
					LogHelper.Error("Exception caught debugging controller input events: " + ex.toString());
				}
			}

			mc.inGameHasFocus = true;

			// hack in sprint
			if (ModVersionHelper.getVersion() == 164)
			{
				if (ControllerSettings.get(JoyBindingEnum.joyBindRun).wasPressed())
				{
					mc.thePlayer.setSprinting(true);
					continue;
				}
			}

			for (ControllerBinding binding : ControllerSettings.getGameAutoHandleBindings())
			{
				if (binding.bindingOptions.contains(BindingOptions.RENDER_TICK))
				{
					if (binding.wasPressed(false))
					{
						preRenderGameBucket.add(binding);
					}
				}
				else
				{
					binding.wasPressed();
				}
			}

		}
	}

	private static void ReplaceControlScreen(GuiControls gui)
	{
		if (!(mc.currentScreen instanceof JoypadConfigMenu))
		{
			try
			{
				LogHelper.Debug("Replacing control screen");
				String[] names = McObfuscationHelper.getMcVarNames("parentScreen");
				GuiScreen parent = ObfuscationReflectionHelper.getPrivateValue(GuiControls.class, (GuiControls) gui,
						names[0], names[1]);
				mc.displayGuiScreen(new JoypadConfigMenu(parent, gui));
			}
			catch (Exception ex)
			{
				LogHelper.Error("Failed to get parent of options gui.  aborting. Exception " + ex.toString());
				return;
			}
		}
	}

	public static boolean InGameCheckNeeded()
	{
		if (!CheckIfModEnabled() || mc.currentScreen != null || mc.thePlayer == null)
		{
			return false;
		}

		return true;
	}

	public static boolean InGuiCheckNeeded()
	{
		if (!CheckIfModEnabled() || mc.currentScreen == null)
		{
			return false;
		}

		return true;
	}

	public static boolean CheckIfModEnabled()
	{
		if (mc == null || !JoypadMod.controllerSettings.isInputEnabled() || ControllerSettings.joystick == null)
		{
			return false;
		}

		return true;
	}
}
