package me.orange.crtangarine.client

import me.orange.crtangarine.network.CommitCameraAimPayload
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor

object ClientInputHandler {
    var isAiming = false
    var aimingCameraPos: BlockPos? = null
    private var originalState: net.minecraft.world.level.block.state.BlockState? = null

    fun startAiming(pos: BlockPos) {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level != null) {
            originalState = level.getBlockState(pos)
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)
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
}
