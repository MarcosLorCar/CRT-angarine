package me.orange.crtangarine.block

import me.orange.crtangarine.Crtangarine
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.level.material.MapColor
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModBlocks {
    val REGISTRY = DeferredRegister.createBlocks(Crtangarine.ID)
    val ITEM_REGISTRY = DeferredRegister.createItems(Crtangarine.ID)
    val CREATIVE_TAB_REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Crtangarine.ID)

    val CAMERA_BLOCK by REGISTRY.register("camera_block") { ->
        Block(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops()
                .strength(5.0f, 6.0f)
                .sound(SoundType.METAL)
        )
    }

    val CAMERA_BLOCK_ITEM by ITEM_REGISTRY.register("camera_block") { ->
        BlockItem(CAMERA_BLOCK, Item.Properties())
    }

    val EXAMPLE_TAB by CREATIVE_TAB_REGISTRY.register("example_tab") { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crtangarine"))
            .icon { ItemStack(CAMERA_BLOCK) }
            .displayItems { _, output ->
                output.accept(CAMERA_BLOCK_ITEM)
            }
            .build()
    }
}
