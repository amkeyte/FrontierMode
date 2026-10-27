package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * The holder class for the admin ingress. {@link AdminToolsModule#init()} registers {@link #build()}
 * through a lambda, so this class loads when the server side boots.
 *
 * <p>Every Forge event here fires far more often than an admin tool is involved (every swing, every hit,
 * every death), so each registration's resolver carries the module's own test and an event is built only
 * for what concerns a tool. The three global consumers hear their event for any firing; the two scoped
 * events are heard by the stash Trinket on the player's own host scope.
 */
final class AdminIngress {

    private AdminIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.ADMIN_INGRESS);

        ingress.translate(AttackEntityEvent.class, AdminIngress::resolveInspectHolder, InspectSwing::new);
        ingress.listenForAll(InspectSwing.class, AdminToolHandlers::onInspectSwing);

        ingress.translate(LivingHurtEvent.class, AdminIngress::resolveDeathTouchAttacker, DeathTouchHit::new);
        ingress.listenForAll(DeathTouchHit.class, AdminToolHandlers::onDeathTouchHit);

        ingress.translate(ItemTossEvent.class, AdminIngress::resolveTosser, SoulboundToss::new);
        ingress.listenForAll(SoulboundToss.class, AdminToolHandlers::onSoulboundToss);

        // Scoped to the player's host scope; the stash Trinket listens for these, so no global consumer.
        ingress.translate(LivingDropsEvent.class, AdminIngress::resolveDyingPlayerScope, SoulboundDeathDrops::new);
        ingress.translate(PlayerEvent.PlayerRespawnEvent.class, AdminIngress::resolveRespawnedScope,
                SoulboundRespawn::new);
        return ingress;
    }

    /** A swing whose player holds Inspect in the main hand. */
    private static Optional<Player> resolveInspectHolder(AttackEntityEvent event) {
        Player player = event.getEntity();
        return StackData.enchantmentLevel(player.getMainHandItem(), AdminToolsModule.INSPECT.get()) > 0
                ? Optional.of(player)
                : Optional.empty();
    }

    /** A hit whose true causer holds Death Touch in the main hand. */
    private static Optional<LivingEntity> resolveDeathTouchAttacker(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && StackData.enchantmentLevel(attacker.getMainHandItem(), AdminToolsModule.DEATH_TOUCH.get()) > 0) {
            return Optional.of(attacker);
        }
        return Optional.empty();
    }

    /** A toss of an admin tool. */
    private static Optional<Player> resolveTosser(ItemTossEvent event) {
        return AdminTools.isAdminTool(event.getEntity().getItem())
                ? Optional.of(event.getPlayer())
                : Optional.empty();
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
}
