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
import net.neoforged.neoforge.network.PacketDistributor

object ClientInputHandler {
    var isAiming = false
    var aimingCameraPos: BlockPos? = null
    private var originalState: net.minecraft.world.level.block.state.BlockState? = null

    private val HUD_TEXTURE = ResourceLocation.fromNamespaceAndPath("crtangarine", "textures/gui/camera_hud.png")

    var cameraYaw = 0f
    var cameraPitch = 0f
    var originalYaw = 0f
    var originalPitch = 0f

    fun startAiming(pos: BlockPos) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level != null && player != null) {
            originalState = level.getBlockState(pos)
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)

            // Read initial camera orientation
            val cameraBe = level.getBlockEntity(pos) as? me.orange.crtangarine.block.CameraBlockEntity
            val state = originalState
            val facing = if (state != null && state.hasProperty(me.orange.crtangarine.block.CameraBlock.FACING)) {
                state.getValue(me.orange.crtangarine.block.CameraBlock.FACING)
            } else {
                net.minecraft.core.Direction.NORTH
            }
            val yaw = cameraBe?.yaw ?: facing.toYRot()
            val pitch = cameraBe?.pitch ?: 0.0f

            cameraYaw = yaw
            cameraPitch = pitch
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
            val pos = aimingCameraPos
            if (pos != null) {
                // Send our accumulated camera angles rather than the frozen player entity angles
                PacketDistributor.sendToServer(CommitCameraAimPayload(pos, cameraPitch, cameraYaw))

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
