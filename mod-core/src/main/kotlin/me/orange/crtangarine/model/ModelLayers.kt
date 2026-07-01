package me.orange.crtangarine.model

import me.orange.crtangarine.Crtangarine
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.resources.ResourceLocation

object ModelLayers {
    val SECURITY_CAMERA = ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(Crtangarine.ID, "security_camera"),
        "main"
    )
}