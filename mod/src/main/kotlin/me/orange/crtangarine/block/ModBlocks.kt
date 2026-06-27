package me.orange.crtangarine.block

import me.orange.crtangarine.Crtangarine
import me.orange.crtangarine.item.SecurityKeycardItem
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
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModBlocks {
    val REGISTRY = DeferredRegister.createBlocks(Crtangarine.ID)
    val ITEM_REGISTRY = DeferredRegister.createItems(Crtangarine.ID)
    val BLOCK_ENTITY_REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Crtangarine.ID)
    val MENU_REGISTRY = DeferredRegister.create(Registries.MENU, Crtangarine.ID)
    val CREATIVE_TAB_REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Crtangarine.ID)

    val CAMERA_BLOCK by REGISTRY.register("camera_block") { ->
        CameraBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops()
                .strength(5.0f, 6.0f)
                .sound(SoundType.COPPER)
                .noOcclusion()
        )
    }

    val CAMERA_STATION_BLOCK by REGISTRY.register("camera_station") { ->
        CameraStationBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops()
                .strength(5.0f, 6.0f)
                .sound(SoundType.HEAVY_CORE)
                .noOcclusion()
        )
    }

    val CAMERA_BLOCK_ITEM by ITEM_REGISTRY.register("camera_block") { ->
        BlockItem(CAMERA_BLOCK, Item.Properties())
    }

    val CAMERA_STATION_ITEM by ITEM_REGISTRY.register("camera_station") { ->
        BlockItem(CAMERA_STATION_BLOCK, Item.Properties())
    }

    val SECURITY_KEYCARD_ITEM by ITEM_REGISTRY.register("security_keycard") { ->
        SecurityKeycardItem(Item.Properties().stacksTo(1))
    }

    val CAMERA_BLOCK_ENTITY_TYPE by BLOCK_ENTITY_REGISTRY.register("camera_block_entity") { ->
        val type: com.mojang.datafixers.types.Type<*>? = null
        BlockEntityType.Builder.of({ pos, state -> CameraBlockEntity(pos, state) }, CAMERA_BLOCK).build(type)
    }

    val CAMERA_STATION_BLOCK_ENTITY_TYPE by BLOCK_ENTITY_REGISTRY.register("camera_station_block_entity") { ->
        val type: com.mojang.datafixers.types.Type<*>? = null
        BlockEntityType.Builder.of({ pos, state -> CameraStationBlockEntity(pos, state) }, CAMERA_STATION_BLOCK).build(type)
    }

    val CAMERA_STATION_MENU_TYPE by MENU_REGISTRY.register("camera_station_menu") { ->
        net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create { windowId, inv, data ->
            CameraStationMenu(windowId, inv, data.readBlockPos())
        }
    }




    val EXAMPLE_TAB by CREATIVE_TAB_REGISTRY.register("example_tab") { ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crtangarine"))
            .icon { ItemStack(CAMERA_BLOCK) }
            .displayItems { _, output ->
                output.accept(CAMERA_BLOCK_ITEM)
                output.accept(CAMERA_STATION_ITEM)
                output.accept(SECURITY_KEYCARD_ITEM)
            }
            .build()
    }
}
