package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.common.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Team;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Inspect Stick's report: what a living entity is, assembled as plain lines. Everything is read
 * through each module's public surface ({@code BossAPI}, {@code BorderAPI}, the sentinel lookup);
 * lines that don't apply are left out. See wiki admin-tools.md, "Inspect".
 */
final class InspectReport {

    private InspectReport() {
    }

    static List<String> lines(LivingEntity target, ServerLevel level) {
        UUID uuid = target.getUUID();
        BlockPos pos = target.blockPosition();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        Team team = target.getTeam();
        String teamName = team == null ? "-" : team.getName();

        List<String> lines = new ArrayList<>();
        lines.add(String.format("[Inspect] %s #%s  HP %.1f/%.1f  (%d, %d, %d)",
                typeId, Ids.shortId(uuid), target.getHealth(), target.getMaxHealth(),
                pos.getX(), pos.getY(), pos.getZ()));
        lines.add("  category " + target.getType().getCategory() + " | team " + teamName
                + " | frontier distance " + BorderAPI.frontierDistanceAt(level, pos));
        lines.add("  boss: " + bossLine(level, uuid));
        sentinelLine(level, target).ifPresent(lines::add);
        if ("feral".equals(teamName)) {
            lines.add("  feral: yes");
        }
        return lines;
    }

    private static String bossLine(ServerLevel level, UUID entityId) {
        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            return "-";
        }
        BossFixture fixture = fixtureOpt.get();
        for (BossRecord record : fixture.all()) {
            if (entityId.equals(record.bossEntityId())) {
                return String.format("#%s layer %d border %s %s%s scale %.2f",
                        Ids.shortId(record.bossId()), record.layer(),
                        record.borderId().map(Ids::shortId).orElse("-"),
                        record.alive() ? "alive" : "defeated",
                        record.materialized() ? ", materialized" : "",
                        fixture.RULES.scaleForLayer(record.layer()));
            }
        }
        return "-";
    }

    private static Optional<String> sentinelLine(ServerLevel level, LivingEntity target) {
        return BossAPI.sentinelsQuiet(level)
                .flatMap(g -> g.record(target.getUUID()))
                .map((BossSentinelsFixture.SentinelRecord record) -> String.format(
                        "  Sentinel: scale %.2f, boss #%s%s",
                        record.scale(), Ids.shortId(record.bossId()),
                        record.doomed() ? ", doomed" : ""));
    }
}
