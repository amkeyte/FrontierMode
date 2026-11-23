package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collection;
import java.util.Optional;

/**
 * {@code neoforge-1.21.1}'s ingress for the admin module: NeoForge's attack, incoming-damage, toss, drops and respawn
 * events translated to {@link InspectSwing}, {@link DeathTouchHit}, {@link SoulboundToss}, {@link SoulboundDeathDrops}
 * and {@link SoulboundRespawn}, with {@link AdminConsumers} installed. Every loader event here fires far more often than
 * an admin tool is involved (every swing, every hit, every death), so each registration's resolver calls the module's
 * own test, held on its interface, and an event is built only for what concerns a tool. The incoming-damage event is
 * the stage before armor (Two Lines § Damage stages on each line). The mod entry registers {@link #build()} through a
 * lambda, so this class loads when the server side boots.
 */
public final class AdminIngress {

    private AdminIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.ADMIN_INGRESS);
        ingress.consumers(AdminConsumers.declare());
        ingress.translate(AttackEntityEvent.class, InspectSwing.class, AdminIngress::resolveInspectHolder,
                ForgeInspectSwing::new);
        ingress.translate(LivingIncomingDamageEvent.class, DeathTouchHit.class, AdminIngress::resolveDeathTouchAttacker,
                ForgeDeathTouchHit::new);
        ingress.translate(ItemTossEvent.class, SoulboundToss.class, AdminIngress::resolveTosser,
                ForgeSoulboundToss::new);
        // Scoped to the player's host scope; the stash Trinket listens for these, so no global consumer.
        ingress.translate(LivingDropsEvent.class, SoulboundDeathDrops.class, AdminIngress::resolveDyingPlayerScope,
                ForgeSoulboundDeathDrops::new);
        ingress.translate(PlayerEvent.PlayerRespawnEvent.class, SoulboundRespawn.class,
                AdminIngress::resolveRespawnedScope, ForgeSoulboundRespawn::new);
        return ingress;
    }

    /** A swing whose player holds Inspect in the main hand. */
    private static Optional<Player> resolveInspectHolder(AttackEntityEvent event) {
        Player player = event.getEntity();
        return InspectSwing.passes(player.getMainHandItem()) ? Optional.of(player) : Optional.empty();
    }

    /** A hit whose true causer holds Death Touch in the main hand. */
    private static Optional<LivingEntity> resolveDeathTouchAttacker(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && DeathTouchHit.passes(attacker.getMainHandItem())) {
            return Optional.of(attacker);
        }
        return Optional.empty();
    }

    /** A toss of an admin tool. */
    private static Optional<Player> resolveTosser(ItemTossEvent event) {
        return SoulboundToss.passes(event.getEntity().getItem()) ? Optional.of(event.getPlayer()) : Optional.empty();
    }

    /** A player's death: the player's host scope, if it is ready. */
    private static Optional<ScopeInfo> resolveDyingPlayerScope(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            return Scopes.of(HostJigs.PLAYER_HOST, player);
        }
        return Optional.empty();
    }

    /** A respawn: the respawned player's host scope, if it is ready. */
    private static Optional<ScopeInfo> resolveRespawnedScope(PlayerEvent.PlayerRespawnEvent event) {
        return Scopes.of(HostJigs.PLAYER_HOST, event.getEntity());
    }

    /** {@link InspectSwing} over NeoForge's attack-entity event, named as Satchel names its own. */
    static final class ForgeInspectSwing extends ModuleSignal<AttackEntityEvent, Player> implements InspectSwing {

        private final Entity target;

        ForgeInspectSwing(AttackEntityEvent forgeEvent, Player player) {
            super(forgeEvent, player);
            this.target = forgeEvent.getTarget();
        }

        @Override
        public Entity target() {
            return target;
        }

        @Override
        public void stopAttack() {
            nativeEvent().setCanceled(true);
        }
    }

    /** {@link DeathTouchHit} over NeoForge's incoming-damage event. */
    static final class ForgeDeathTouchHit extends ModuleSignal<LivingIncomingDamageEvent, LivingEntity>
            implements DeathTouchHit {

        ForgeDeathTouchHit(LivingIncomingDamageEvent forgeEvent, LivingEntity attacker) {
            super(forgeEvent, attacker);
        }

        @Override
        public void overwhelm() {
            nativeEvent().setAmount(Float.MAX_VALUE);
        }
    }

    /** {@link SoulboundToss} over NeoForge's item-toss event. */
    static final class ForgeSoulboundToss extends ModuleSignal<ItemTossEvent, Player> implements SoulboundToss {

        private final ItemStack stack;

        ForgeSoulboundToss(ItemTossEvent forgeEvent, Player player) {
            super(forgeEvent, player);
            this.stack = forgeEvent.getEntity().getItem();
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public void cancel() {
            nativeEvent().setCanceled(true);
        }
    }

    /** {@link SoulboundDeathDrops} over NeoForge's living-drops event. */
    static final class ForgeSoulboundDeathDrops extends ModuleSignal<LivingDropsEvent, ScopeInfo>
            implements SoulboundDeathDrops {

        ForgeSoulboundDeathDrops(LivingDropsEvent forgeEvent, ScopeInfo scope) {
            super(forgeEvent, scope);
        }

        @Override
        public Collection<ItemEntity> drops() {
            return nativeEvent().getDrops();
        }
    }

    /** {@link SoulboundRespawn} over NeoForge's player-respawn event. */
    static final class ForgeSoulboundRespawn extends ModuleSignal<PlayerEvent.PlayerRespawnEvent, ScopeInfo>
            implements SoulboundRespawn {

        private final Player player;

        ForgeSoulboundRespawn(PlayerEvent.PlayerRespawnEvent forgeEvent, ScopeInfo scope) {
            super(forgeEvent, scope);
            this.player = forgeEvent.getEntity();
        }

        @Override
        public Player player() {
            return player;
        }
    }
}
