package me.orange.crtangarine.client

import com.mojang.blaze3d.systems.RenderSystem
import me.orange.crtangarine.network.CommitCameraAimPayload
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.decoration.ArmorStand
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object ClientInputHandler {
    var isAiming = false
    var aimingCameraPos: BlockPos? = null
    private var originalState: net.minecraft.world.level.block.state.BlockState? = null
    private var dummyCameraEntity: ArmorStand? = null

    private val HUD_TEXTURE = ResourceLocation.fromNamespaceAndPath("crtangarine", "textures/gui/camera_hud.png")

    fun startAiming(pos: BlockPos) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level != null && player != null) {
            originalState = level.getBlockState(pos)
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)

            // Spawn client-side dummy ArmorStand. Center it horizontally, and adjust Y so its eyes align with block center.
            val dummy = ArmorStand(level, pos.x + 0.5, pos.y + 0.5 - player.eyeHeight, pos.z + 0.5)
            dummy.isInvisible = true
            dummy.isNoGravity = true

            // Read original camera orientation
            val cameraBe = level.getBlockEntity(pos) as? me.orange.crtangarine.block.CameraBlockEntity
            val state = originalState
            val facing = if (state != null && state.hasProperty(me.orange.crtangarine.block.CameraBlock.FACING)) {
                state.getValue(me.orange.crtangarine.block.CameraBlock.FACING)
            } else {
                net.minecraft.core.Direction.NORTH
            }
            val yaw = cameraBe?.yaw ?: facing.toYRot()
            val pitch = cameraBe?.pitch ?: 0.0f

            dummy.setYRot(yaw)
            dummy.setXRot(pitch)
            dummy.yRotO = yaw
            dummy.xRotO = pitch

            // Set local player's look rotation to match the camera initial facing
            player.yRot = yaw
            player.xRot = pitch
            player.yRotO = yaw
            player.xRotO = pitch

            level.addFreshEntity(dummy)
            dummyCameraEntity = dummy
            mc.setCameraEntity(dummy)

            // Hide standard HUD elements (vanilla F1 mode)
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

        mc.player?.let { mc.setCameraEntity(it) }
        dummyCameraEntity?.discard()
        dummyCameraEntity = null

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
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity
        if (player.level().isClientSide && isAiming) {
            val dummy = dummyCameraEntity
            if (dummy != null) {
                // Copy local player mouse looking direction to the dummy camera entity
                dummy.setYRot(player.yRot)
                dummy.setXRot(player.xRot)
                dummy.yRotO = player.yRotO
                dummy.xRotO = player.xRotO
            }
        }
    }

    @SubscribeEvent
    fun onComputeCameraAngles(event: ViewportEvent.ComputeCameraAngles) {
        if (isAiming) {
            val mc = Minecraft.getInstance()
            val player = mc.player
            val dummy = dummyCameraEntity
            if (player != null && dummy != null) {
                // Smoothly update dummy orientation on every frame
                dummy.setYRot(player.yRot)
                dummy.setXRot(player.xRot)
                dummy.yRotO = player.yRotO
                dummy.xRotO = player.xRotO

                event.yaw = player.yRot
                event.pitch = player.xRot
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
