package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * NeoForge's half of {@link SpawnGateHealthCheck}: its events on {@code MobSpawnEvent.SpawnPlacementCheck},
 * {@code MobSpawnEvent.PositionCheck} and {@code LivingConversionEvent.Pre}, and the two calls through
 * {@code EventHooks} that fire the last two. The two spawn checks each carry their own result type
 * ({@code SUCCEED}, {@code DEFAULT}, {@code FAIL}), which their classes bind in the constructor. The check is shared.
 */
public final class SpawnGateIngress {

    private SpawnGateIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:spawn_gates");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
        SpawnGateHealthCheck.register(new ForgeLoader());
    }

    /** Forge's calls for the position check and the pre-conversion event. */
    static final class ForgeLoader implements SpawnGateHealthCheck.Loader {
        @Override
        public boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level) {
            return EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
        }

        @Override
        public boolean canConvert(Mob mob, EntityType<? extends LivingEntity> outcome) {
            return EventHooks.canLivingConvert(mob, outcome, timer -> { });
        }
    }

    static final class ForgePlacementGate extends ModuleSignal<MobSpawnEvent.SpawnPlacementCheck, Level>
            implements SpawnGateHealthCheck.PlacementGate {
        ForgePlacementGate(MobSpawnEvent.SpawnPlacementCheck e, Level level) {
            super(e, level);
            bindResult(() -> switch (e.getResult()) {
                case SUCCEED -> Verdict.ALLOW;
                case FAIL -> Verdict.DENY;
                case DEFAULT -> Verdict.DEFAULT;
            }, v -> e.setResult(switch (v) {
                case ALLOW -> MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
                case DENY -> MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
                case DEFAULT -> MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT;
            }));
            SpawnGateHealthCheck.onPlacementBuilt(e.getDefaultResult());
        }
    }

    static final class ForgePositionGate extends ModuleSignal<MobSpawnEvent.PositionCheck, Mob>
            implements SpawnGateHealthCheck.PositionGate {
        ForgePositionGate(MobSpawnEvent.PositionCheck e, Mob mob) {
            super(e, mob);
            bindResult(() -> switch (e.getResult()) {
                case SUCCEED -> Verdict.ALLOW;
                case FAIL -> Verdict.DENY;
                case DEFAULT -> Verdict.DEFAULT;
            }, v -> e.setResult(switch (v) {
                case ALLOW -> MobSpawnEvent.PositionCheck.Result.SUCCEED;
                case DENY -> MobSpawnEvent.PositionCheck.Result.FAIL;
                case DEFAULT -> MobSpawnEvent.PositionCheck.Result.DEFAULT;
            }));
            SpawnGateHealthCheck.onGateBuilt();
        }
    }

    static final class ForgeConversionGate extends ModuleSignal<LivingConversionEvent.Pre, LivingEntity>
            implements SpawnGateHealthCheck.ConversionGate {
        ForgeConversionGate(LivingConversionEvent.Pre e, LivingEntity mob) {
            super(e, mob);
            SpawnGateHealthCheck.onGateBuilt();
        }

        @Override
        public void cancel() {
            nativeEvent().setCanceled(true);
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.translate(MobSpawnEvent.SpawnPlacementCheck.class, SpawnGateHealthCheck.PlacementGate.class,
                e -> SpawnGateHealthCheck.placementContext(e.getLevel().getLevel()), ForgePlacementGate::new);
        ingress.translate(MobSpawnEvent.PositionCheck.class, SpawnGateHealthCheck.PositionGate.class,
                e -> SpawnGateHealthCheck.positionContext(e.getEntity()), ForgePositionGate::new);
        ingress.translate(LivingConversionEvent.Pre.class, SpawnGateHealthCheck.ConversionGate.class,
                e -> SpawnGateHealthCheck.conversionContext(e.getEntity()), ForgeConversionGate::new);
        ingress.consumers(SpawnGateHealthCheck.consumers());
        return ingress;
    }
}
