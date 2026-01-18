package com.moepus.serverwarashi.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.ChunkLoadInfo;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.*;

@Mixin(value = ServerWatchdog.class, remap = false)
public class ServerWatchdogMixin {
    @Shadow
    @Final
    private DedicatedServer server;

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;saveToFile(Ljava/nio/file/Path;Lnet/minecraft/ReportType;)Z"))
    private boolean onSaveToFile(CrashReport instance, Path path, ReportType type, Operation<Boolean> original) {
        instance.saveToFile(path, type);

        try {
            Files.writeString(path, "\n\n==== ServerWarashi Ticket Info ====", StandardOpenOption.APPEND);

            // append ticket info to the crash report file
            for (var level : server.getAllLevels()) {
                Files.writeString(path, "\n-- Dimension: " + level.dimension().location() + " --\n", StandardOpenOption.APPEND);
                var ticketInfo = ChunkLoadInfo.dumpTickets(level, true, false);
                Files.writeString(path, ticketInfo.toString(), StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
