package me.orange.crtangarine.client

import com.mojang.blaze3d.systems.RenderSystem
import me.orange.crtangarine.network.CommitCameraAimPayload
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderPlayerEvent
import net.neoforged.neoforge.network.PacketDistributor

object ClientInputHandler {
    var isAiming = false
    var aimingCameraPos: BlockPos? = null
    private var originalState: net.minecraft.world.level.block.state.BlockState? = null

    private val HUD_TEXTURE = ResourceLocation.fromNamespaceAndPath("crtangarine", "textures/gui/camera_hud.png")

    var originalYaw = 0f
    var originalPitch = 0f

    private var tempYaw = 0f
    private var tempPitch = 0f
    private var tempYawO = 0f
    private var tempPitchO = 0f
    private var tempHeadYaw = 0f
    private var tempHeadYawO = 0f
    private var tempBodyYaw = 0f
    private var tempBodyYawO = 0f

    fun startAiming(pos: BlockPos) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level != null && player != null) {
            originalState = level.getBlockState(pos)
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)

            originalYaw = player.yRot
            originalPitch = player.xRot

            // Hide standard HUD elements
            mc.options.hideGui = true
        }
        aimingCameraPos = pos
        isAiming = true
    }

    fun stopAiming() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val pos = aimingCameraPos
        val state = originalState
        if (level != null && pos != null && state != null) {
            level.setBlock(pos, state, 3)
        }

        mc.options.hideGui = false

        isAiming = false
        aimingCameraPos = null
        originalState = null
    }

    @SubscribeEvent
    fun onMouseClick(event: InputEvent.MouseButton.Pre) {
        if (isAiming && event.button == 0 && event.action == 1) {
            val mc = Minecraft.getInstance()
            val player = mc.player
            val pos = aimingCameraPos
            if (player != null && pos != null) {
                val yaw = player.yRot
                val pitch = player.xRot

                PacketDistributor.sendToServer(CommitCameraAimPayload(pos, pitch, yaw))

                stopAiming()
                event.isCanceled = true
            }
        }
    }

    @SubscribeEvent
    fun onMovementInputUpdate(event: MovementInputUpdateEvent) {
        if (isAiming) {
            // Cancel player physical movement inputs when looking through camera
            val input = event.input
            input.forwardImpulse = 0f
            input.leftImpulse = 0f
            input.up = false
            input.down = false
            input.left = false
            input.right = false
            input.jumping = false
            input.shiftKeyDown = false
        }
    }

    @SubscribeEvent
    fun onRenderPlayerPre(event: RenderPlayerEvent.Pre) {
        val player = event.entity
        val mc = Minecraft.getInstance()
        if (isAiming && player == mc.player) {
            // Save current rotation fields
            tempYaw = player.yRot
            tempPitch = player.xRot
            tempYawO = player.yRotO
            tempPitchO = player.xRotO
            tempHeadYaw = player.yHeadRot
            tempHeadYawO = player.yHeadRotO
            tempBodyYaw = player.yBodyRot
            tempBodyYawO = player.yBodyRotO

            // Force rendering look direction to the console's original orientation
            player.yRot = originalYaw
            player.xRot = originalPitch
            player.yRotO = originalYaw
            player.xRotO = originalPitch
            player.yHeadRot = originalYaw
            player.yHeadRotO = originalYaw
            player.yBodyRot = originalYaw
            player.yBodyRotO = originalYaw
        }
    }

    @SubscribeEvent
    fun onRenderPlayerPost(event: RenderPlayerEvent.Post) {
        val player = event.entity
        val mc = Minecraft.getInstance()
        if (isAiming && player == mc.player) {
            // Restore original rotation fields so aiming works smoothly
            player.yRot = tempYaw
            player.xRot = tempPitch
            player.yRotO = tempYawO
            player.xRotO = tempPitchO
            player.yHeadRot = tempHeadYaw
            player.yHeadRotO = tempHeadYawO
            player.yBodyRot = tempBodyYaw
            player.yBodyRotO = tempBodyYawO
        }
    }

    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        if (isAiming) {
            val mc = Minecraft.getInstance()
            val guiGraphics = event.guiGraphics
            val width = mc.window.guiScaledWidth
            val height = mc.window.guiScaledHeight

            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)

            // Render custom camera overlay covering the whole screen
            guiGraphics.blit(HUD_TEXTURE, 0, 0, 0f, 0f, width, height, width, height)

            RenderSystem.disableBlend()
        }
    }
}
