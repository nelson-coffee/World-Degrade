package dev.ncn.worlddegrade.compat.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

final class TrainWrecker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TrainWrecker() {
    }

    static List<CarriageContraptionEntity> presentEntities(Train train) {
        List<CarriageContraptionEntity> entities = new ArrayList<>();
        for (Carriage carriage : train.carriages) {
            carriage.forEachPresentEntity(entities::add);
        }
        return entities;
    }

    static AABB carriageBounds(Train train) {
        AABB bounds = null;
        for (CarriageContraptionEntity entity : presentEntities(train)) {
            AABB box = ContraptionDegrader.rotationSafeScanBox(entity);
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        return bounds;
    }

    static boolean teardown(Train train) {
        try {
            List<CarriageContraptionEntity> entities = presentEntities(train);
            if (entities.isEmpty()) {
                return false;
            }
            train.speed = 0;
            train.targetSpeed = 0;
            train.throttle = 0;
            if (train.navigation != null) {
                train.navigation.cancelNavigation();
            }
            if (train.canDisassemble() && cleanDisassemble(train, entities.get(0))) {
                return true;
            }
            forceTeardown(train, entities);
            return true;
        } catch (Throwable t) {
            LOGGER.error("World Degrade: failed to wreck train {}", train.id, t);
            return false;
        }
    }

    private static boolean cleanDisassemble(Train train, CarriageContraptionEntity lead) {
        Direction orientation = lead.getInitialOrientation();
        if (orientation == null) {
            return false;
        }
        BlockPos anchor = lead.blockPosition();
        return train.disassemble(orientation, anchor);
    }

    private static void forceTeardown(Train train, List<CarriageContraptionEntity> entities) {
        for (CarriageContraptionEntity entity : entities) {
            entity.disassemble();
        }
        train.detachFromTracks();
        Create.RAILWAYS.removeTrain(train.id);
        Create.RAILWAYS.markTracksDirty();
    }
}
