# RM_FRO_037 / FRO_099 test aid: scatters ~two dozen rabbits around wherever this is run from,
# instead of relying on natural spawns, so Sick Wildlife's cosmetic tell (ExteriorTellFixture)
# has something to act on immediately. NOTE: these are /summon'd, not naturally spawned or
# spawner-spawned, so they do NOT pass through MobSpawnEvent.FinalizeSpawn -- the density
# companion hook (BorderModule.onMobSpawnFinalize) will not fire for any of these. This function
# only exercises the cosmetic tell, not the density boost.
execute positioned ~7 ~ ~5 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-12 ~ ~5 run summon minecraft:rabbit ~ ~ ~
execute positioned ~7 ~ ~12 run summon minecraft:rabbit ~ ~ ~
execute positioned ~9 ~ ~12 run summon minecraft:rabbit ~ ~ ~
execute positioned ~6 ~ ~2 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-13 ~ ~6 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-3 ~ ~0 run summon minecraft:rabbit ~ ~ ~
execute positioned ~2 ~ ~11 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-11 ~ ~7 run summon minecraft:rabbit ~ ~ ~
execute positioned ~0 ~ ~5 run summon minecraft:rabbit ~ ~ ~
execute positioned ~11 ~ ~-5 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-2 ~ ~9 run summon minecraft:rabbit ~ ~ ~
execute positioned ~2 ~ ~0 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-10 ~ ~11 run summon minecraft:rabbit ~ ~ ~
execute positioned ~14 ~ ~12 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-13 ~ ~-12 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-1 ~ ~-12 run summon minecraft:rabbit ~ ~ ~
execute positioned ~3 ~ ~-5 run summon minecraft:rabbit ~ ~ ~
execute positioned ~-3 ~ ~6 run summon minecraft:rabbit ~ ~ ~
execute positioned ~8 ~ ~-1 run summon minecraft:rabbit ~ ~ ~
execute positioned ~12 ~ ~4 run summon minecraft:rabbit ~ ~ ~
execute positioned ~4 ~ ~-3 run summon minecraft:rabbit ~ ~ ~
execute positioned ~6 ~ ~8 run summon minecraft:rabbit ~ ~ ~
execute positioned ~4 ~ ~-13 run summon minecraft:rabbit ~ ~ ~
