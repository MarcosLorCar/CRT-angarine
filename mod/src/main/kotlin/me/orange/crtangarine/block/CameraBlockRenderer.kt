package me.orange.crtangarine.block

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import me.orange.crtangarine.Crtangarine
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.BlockRenderDispatcher
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelData

class CameraBlockRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<CameraBlockEntity> {
    // The BlockRenderDispatcher lets us draw arbitrary block JSON models
    private val blockRenderer: BlockRenderDispatcher = context.blockRenderDispatcher

    // Define pointers to your two JSON models
    private val baseplateModel = ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "block/security_camera_baseplate"),
        "standalone"
    )

    private val bodyModel = ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "block/security_camera_body"),
        "standalone"
    )
    override fun render(
        camera: CameraBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val level = camera.level ?: return
        val pos = camera.blockPos
        val state: BlockState = level.getBlockState(pos)

        // Find the models in the game's compiled asset manager
        val manager = blockRenderer.blockModelShaper.modelManager
        val baseplateBaked = blockRenderer.blockModelShaper.getBlockModel(state)
        val bodyBaked = manager.getModel(bodyModel)

        // 1. Render the static Baseplate
        blockRenderer.modelRenderer.renderModel(
            poseStack.last(),
            bufferSource.getBuffer(RenderType.cutout()),
            state,
            baseplateBaked,
            1.0f, 1.0f, 1.0f,
            packedLight,
            packedOverlay,
            ModelData.EMPTY,
            RenderType.cutout()
        )

        // 2. Isolate transformations for the moving body
        poseStack.pushPose()

        // 3. Move the coordinate system to your Blockbench pivot point
        // Adjust these offsets to match where your hinge sits in 3D space!
        poseStack.translate(0.5, 0.5, 0.5)

        // 4. Apply calculations (JSON rendering still uses Mojang Axis calculations)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - camera.yaw))
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-camera.pitch))

        // Move the drawing alignment back from the pivot center so it sits correctly
        poseStack.translate(-0.5, -0.5, -0.5)

        // 5. Render the Camera Body JSON at the rotated positions
        blockRenderer.modelRenderer.renderModel(
            poseStack.last(),
            bufferSource.getBuffer(RenderType.cutout()),
            state,
            bodyBaked,
            1.0f, 1.0f, 1.0f,
            packedLight,
            packedOverlay,
            ModelData.EMPTY,
            RenderType.cutout()
        )

        poseStack.popPose()
    }
}