package me.orange.crtangarine.network

import net.neoforged.neoforge.network.handling.IPayloadContext
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

object ClientPacketHandler {
    fun handleStartAimMode(payload: StartAimModePayload, context: IPayloadContext) {
        context.enqueueWork {
            runForDist(
                clientTarget = { {
                    me.orange.crtangarine.client.ClientInputHandler.startAiming(payload.pos)
                } },
                serverTarget = { { } }
            ).invoke()
        }
    }
}
