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
            if (pos != null) {
                // Smoothly override camera rendering coordinates to the camera block center
                this.position = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                this.blockPosition.set(pos)

                // Override camera rendering angles to match entity's actual looking rotation
                this.xRot = entity.xRot
                this.yRot = entity.yRot
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
