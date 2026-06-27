package me.orange.crtangarine.mixin

import net.minecraft.client.Camera
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Camera::class)
abstract class CameraMixin {
    @Shadow
    private var position: Vec3 = Vec3.ZERO

    @Shadow
    @Final
    private lateinit var blockPosition: BlockPos.MutableBlockPos

    @Shadow
    private var xRot: Float = 0.0f

    @Shadow
    private var yRot: Float = 0.0f

    @Inject(method = ["setup"], at = [At("RETURN")])
    fun onSetup(
        level: BlockGetter,
        entity: Entity,
        detached: Boolean,
        thirdPersonFront: Boolean,
        partialTicks: Float,
        ci: CallbackInfo
    ) {
        if (me.orange.crtangarine.client.ClientInputHandler.isAiming) {
            val pos = me.orange.crtangarine.client.ClientInputHandler.aimingCameraPos
            val mc = net.minecraft.client.Minecraft.getInstance()
            val player = mc.player
            if (pos != null && player != null) {
                // Smoothly override camera rendering coordinates to the camera block center
                this.position = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                this.blockPosition.set(pos)

                // Calculate mouse look deltas from the fixed original orientation
                val deltaYaw = player.yRot - me.orange.crtangarine.client.ClientInputHandler.originalYaw
                val deltaPitch = player.xRot - me.orange.crtangarine.client.ClientInputHandler.originalPitch

                // Accumulate the delta into our separate camera angles
                me.orange.crtangarine.client.ClientInputHandler.cameraYaw += deltaYaw
                me.orange.crtangarine.client.ClientInputHandler.cameraPitch = 
                    (me.orange.crtangarine.client.ClientInputHandler.cameraPitch + deltaPitch).coerceIn(-90f, 90f)

                // Freeze the player model's head and body at the original console orientation
                player.yRot = me.orange.crtangarine.client.ClientInputHandler.originalYaw
                player.xRot = me.orange.crtangarine.client.ClientInputHandler.originalPitch
                player.yRotO = me.orange.crtangarine.client.ClientInputHandler.originalYaw
                player.xRotO = me.orange.crtangarine.client.ClientInputHandler.originalPitch

                // Force the rendered camera angles to use the accumulated angles
                this.xRot = me.orange.crtangarine.client.ClientInputHandler.cameraPitch
                this.yRot = me.orange.crtangarine.client.ClientInputHandler.cameraYaw
            }
        }
    }

    @Inject(method = ["isDetached"], at = [At("HEAD")], cancellable = true)
    fun onIsDetached(ci: CallbackInfoReturnable<Boolean>) {
        if (me.orange.crtangarine.client.ClientInputHandler.isAiming) {
            ci.returnValue = true
        }
    }
}
