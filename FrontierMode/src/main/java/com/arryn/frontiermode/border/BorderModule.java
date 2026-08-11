package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.Rendering;
import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.server.commands.BorderCommands;
import com.arryn.frontiermode.border.server.rules.BordersTriggers;
import com.arryn.satchel.common.bundle.builder.BundleFactories;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.SatchelJigRegistrar;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.strap.SatchelStrap;
import com.arryn.satchel.common.jig.strap.SatchelStrapRegistrar;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * Entry point for initializing and hooking the Border subsystem.
 */
public final class BorderModule {
    private BorderModule() {
    }


    public static void init() {
        OUT.info("FrontierMode - Border Module Initiating.");
        BundleFactories
                .registerFactory(
                        FrontierKeys.BORDERS_BUNDLE,
                        scope -> new BordersBundle(scope, FrontierKeys.BORDERS_BUNDLE))
                .registerFixture(
                        FrontierKeys.BORDERS,
                        BordersFixture::new);

        SatchelStrapRegistrar.register(new StrapDeclaration());

        SatchelJigRegistrar.register(
                LogicalSide.SERVER,
                FrontierKeys.BORDERS_JIG,
                LevelJig::new);

        SatchelJigRegistrar.register(
                LogicalSide.CLIENT,
                FrontierKeys.BORDERS_JIG,
                LevelJig::new);
    }


    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BorderCommands.register(event.getDispatcher());
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        BordersTriggers.growPath(event);
    }

    private static class StrapDeclaration implements SatchelStrap.Declaration {

        @Override
        public boolean appliesTo(LogicalSide logicalSide) {
            return logicalSide == LogicalSide.SERVER
                    || logicalSide == LogicalSide.CLIENT;
        }

        @Override
        public SatchelStrap create() {
            return new BorderStrap();
        }
    }

    public static class BorderStrap implements SatchelStrap {


        @Override
        public void attach(LogicalFoundation foundation) {
//            foundation.eventBus().subscribe(
//                    ScopeEvent.Loaded.class,
//                    this::onScopeLoaded
//            );
            foundation.eventBus().subscribe(
                    ScopeEvent.Tick.class,
                    BordersTriggers::updateFinderItems
            );
            foundation.eventBus().subscribe(
                    ScopeEvent.Tick.class,
                    Rendering::onClientTick
            );
        }

        @Override
        public void lift(LogicalSide logicalSide) {

        }

        @Override
        public void drop(LogicalSide logicalSide) {
            //currently noop
        }

    }


}
