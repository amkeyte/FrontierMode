package com.arryn.frontiermode;

import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import com.arryn.satchel.common.util.out.OUT;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * FRO_155: drops vanilla's per-death INFO line for FrontierMode's own named mobs.
 *
 * <p>{@code LivingEntity.die()} logs {@code "Named entity {} died: {}"} for every entity with a
 * custom name. Feral mobs and bosses always carry one (shown only in the debug view; a boss's also
 * reaches death messages), and Feral pack density turns that into a flood. The line comes from vanilla's own logger, so mod
 * logging code can't reach it; this installs a log4j filter on that one logger instead. Done from
 * mod code, not a config file, so it applies on any server and any dev run.
 *
 * <p>Deliberately narrow: only lines naming a Feral or boss-named entity are denied. A tamed
 * pet or a player-renamed mob dying still logs, since that line is useful and isn't ours -- unless
 * the player named it exactly like a boss.
 */
final class LogFilters {

    private static final String LIVING_ENTITY_LOGGER = "net.minecraft.world.entity.LivingEntity";

    private LogFilters() {
    }

    static void install() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();

            // A dedicated LoggerConfig for just this logger name (additive, so it still writes to
            // the parents' appenders); the filter is scoped to it and touches nothing else.
            LoggerConfig loggerConfig = new LoggerConfig(LIVING_ENTITY_LOGGER, Level.INFO, true);
            loggerConfig.addFilter(new OwnNamedMobDeathFilter());
            config.addLogger(LIVING_ENTITY_LOGGER, loggerConfig);
            ctx.updateLoggers();
        } catch (RuntimeException | LinkageError e) {
            // Log noise is not worth failing mod load over -- the log4j-core backend isn't a
            // guarantee in every environment.
            OUT.warn("[FrontierMode] Could not install the named-mob death log filter: " + e);
        }
    }

    private static final class OwnNamedMobDeathFilter extends AbstractFilter {

        OwnNamedMobDeathFilter() {
            super(Result.DENY, Result.NEUTRAL);
        }

        @Override
        public Result filter(LogEvent event) {
            String message = event.getMessage().getFormattedMessage();
            if (message.startsWith("Named entity ")
                    && (message.contains("'Feral [") || message.contains("'" + DefaultBossRules.BOSS_NAME + "'"))) {
                return onMatch;
            }
            return onMismatch;
        }
    }
}
