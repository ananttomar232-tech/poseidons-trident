package com.poseidon.trident;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * All server-side behaviour of Poseidon's Trident.
 *
 * Passive powers apply while the trident is in the main hand or off-hand.
 * Active powers last {@link #DURATION_TICKS}, then go on cooldown for {@link #COOLDOWN_TICKS}.
 * An active power also ends early (and starts its cooldown) if the trident is put away.
 */
public final class PowerManager {

    // ---------------------------------------------------------------- tuning
    public static final int DURATION_TICKS = 20 * 60;   // 1 minute
    public static final int COOLDOWN_TICKS = 20 * 10;   // 10 seconds

    // Passive
    private static final double EXTRA_HEALTH = 40.0D;    // +20 hearts -> 40 hearts total

    // Thunder Domain
    private static final double THUNDER_RADIUS = 10.0D;
    private static final int THUNDER_INTERVAL = 15;      // ticks between strikes
    private static final float THUNDER_DAMAGE = 8.0F;    // ~3 strikes kill a 20 HP mob
    private static final int THUNDER_MAX_TARGETS = 8;

    // God Speed
    private static final int CHARGE_TICKS = 30;          // time R must "charge" before P works
    private static final int CHARGE_WINDOW = 200;        // charged state lasts 10 s
    private static final int SPEED_AMPLIFIER = 9;        // Speed X
    private static final double DASH_SPEED = 2.2D;       // blocks per tick during a burst
    private static final int DASH_TICKS = 10;
    private static final float DASH_DAMAGE = 40.0F;      // 20 hearts

    // Water Cube
    private static final double CUBE_HALF = 4.0D;        // 8x8x8 cube around the player

    // Tsunami
    private static final float TSUNAMI_DROWN_DAMAGE = 4.0F;
    private static final int RESTORE_PER_TICK = 1500;

    // ---------------------------------------------------------------- ids / state
    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(PoseidonsTridentMod.MOD_ID, "divine_health");
    private static final String TAG_PASSIVE = PoseidonsTridentMod.MOD_ID + ":passive";
    private static final String TAG_FLIGHT = PoseidonsTridentMod.MOD_ID + ":flight";

    private static final Map<UUID, PlayerPowers> STATES = new ConcurrentHashMap<>();
    private static final List<Flood> RESTORING = new ArrayList<>();

    private PowerManager() {}

    // ================================================================ events

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.player instanceof ServerPlayer player) {
            tick(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        tickRestores(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cleanup(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Put the world back right away so no stray water is left in the save.
        MinecraftServer server = event.getServer();
        for (PlayerPowers st : STATES.values()) {
            if (st.flood != null) {
                RESTORING.add(st.flood);
                st.flood = null;
            }
        }
        for (Flood flood : RESTORING) {
            ServerLevel level = server.getLevel(flood.dimension);
            if (level != null) {
                while (!flood.restoreSome(level, 100_000)) { /* keep going */ }
            }
        }
        RESTORING.clear();
        STATES.clear();
    }

    /** Water Cube makes the owner immune to everything except void / /kill. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerPowers st = STATES.get(player.getUUID());
            if (st != null && st.isActive(Power.WATER_CUBE)
                    && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
            }
        }
    }

    /** Holding the trident = no fall damage. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && isHolding(player)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    // ================================================================ input from client

    public static void handleAction(ServerPlayer player, int ordinal) {
        if (ordinal < 0 || ordinal >= Action.values().length) return;
        if (!player.isAlive() || player.isSpectator()) return;
        if (!isHolding(player)) {
            message(player, Component.translatable("msg.poseidonstrident.need_trident"));
            return;
        }

        long now = player.level().getGameTime();
        PlayerPowers st = STATES.computeIfAbsent(player.getUUID(), id -> new PlayerPowers());

        switch (Action.values()[ordinal]) {
            case THUNDER_DOMAIN -> activate(player, st, Power.THUNDER_DOMAIN, now);
            case WATER_CUBE -> activate(player, st, Power.WATER_CUBE, now);
            case TSUNAMI -> activate(player, st, Power.TSUNAMI, now);
            case CHARGE -> {
                if (st.isActive(Power.GOD_SPEED)) return;
                st.chargeStart = now;
                st.chargeAnnounced = false;
                message(player, Component.translatable("msg.poseidonstrident.charging"));
                playSound(player.serverLevel(), player.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
            }
            case DASH -> {
                if (st.isActive(Power.GOD_SPEED)) {
                    startDash(player, st);   // extra burst while God Speed is running
                } else if (isCharged(st, now)) {
                    st.chargeStart = -1;
                    activate(player, st, Power.GOD_SPEED, now);
                } else {
                    message(player, Component.translatable("msg.poseidonstrident.not_charged"));
                }
            }
            case GOD_SPEED_SMART -> {
                // One key for the whole God Speed flow: charge -> activate -> extra burst
                if (st.isActive(Power.GOD_SPEED)) {
                    startDash(player, st);
                } else if (isCharged(st, now)) {
                    st.chargeStart = -1;
                    activate(player, st, Power.GOD_SPEED, now);
                } else {
                    st.chargeStart = now;
                    st.chargeAnnounced = false;
                    message(player, Component.translatable("msg.poseidonstrident.charging"));
                    playSound(player.serverLevel(), player.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
                }
            }
            case SELECT_THUNDER_DOMAIN -> st.selected = Power.THUNDER_DOMAIN.ordinal();
            case SELECT_GOD_SPEED -> st.selected = Power.GOD_SPEED.ordinal();
            case SELECT_WATER_CUBE -> st.selected = Power.WATER_CUBE.ordinal();
            case SELECT_TSUNAMI -> st.selected = Power.TSUNAMI.ordinal();
        }
    }

    // ================================================================ per-tick logic

    private static void tick(ServerPlayer player) {
        if (!player.isAlive()) {
            cleanup(player);
            return;
        }

        boolean holding = isHolding(player);
        tickPassive(player, holding);
        if (holding) tickAura(player);

        PlayerPowers st = STATES.get(player.getUUID());
        if (st == null) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        // Charge feedback
        if (st.chargeStart >= 0) {
            if (!holding || now > st.chargeStart + CHARGE_TICKS + CHARGE_WINDOW) {
                st.chargeStart = -1;
            } else if (!st.chargeAnnounced && now >= st.chargeStart + CHARGE_TICKS) {
                st.chargeAnnounced = true;
                message(player, Component.translatable("msg.poseidonstrident.charged"));
                playSound(level, player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.8F);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0,
                        player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
            }
        }

        for (Power power : Power.values()) {
            int i = power.ordinal();
            if (st.activeUntil[i] <= 0) continue;

            if (!holding || now >= st.activeUntil[i]) {
                end(player, st, power, now);
                continue;
            }
            switch (power) {
                case THUNDER_DOMAIN -> tickThunderDomain(player, level, now);
                case GOD_SPEED -> tickGodSpeed(player, st, level, now);
                case WATER_CUBE -> tickWaterCube(player, level, now);
                case TSUNAMI -> tickTsunami(player, st, level, now);
            }
        }

        // Status line once a second
        if (now % 20 == 0 && st.anyActive()) {
            StringBuilder sb = new StringBuilder();
            for (Power power : Power.values()) {
                long until = st.activeUntil[power.ordinal()];
                if (until > 0) {
                    if (sb.length() > 0) sb.append("  |  ");
                    sb.append(power.displayName().getString()).append(' ').append((until - now) / 20).append('s');
                }
            }
            message(player, Component.literal(sb.toString()));
        }
    }

    private static void tickRestores(MinecraftServer server) {
        if (RESTORING.isEmpty()) return;
        Iterator<Flood> it = RESTORING.iterator();
        while (it.hasNext()) {
            Flood flood = it.next();
            ServerLevel level = server.getLevel(flood.dimension);
            if (level == null || flood.restoreSome(level, RESTORE_PER_TICK)) {
                it.remove();
            }
        }
    }

    // ================================================================ activation / end

    private static void activate(ServerPlayer player, PlayerPowers st, Power power, long now) {
        int i = power.ordinal();
        if (st.activeUntil[i] > 0) {
            long left = (st.activeUntil[i] - now) / 20;
            message(player, Component.translatable("msg.poseidonstrident.already_active", power.displayName(), left));
            return;
        }
        if (st.cooldownUntil[i] > now) {
            long left = (st.cooldownUntil[i] - now + 19) / 20;
            message(player, Component.translatable("msg.poseidonstrident.cooldown", power.displayName(), left));
            return;
        }

        st.activeUntil[i] = now + DURATION_TICKS;
        ServerLevel level = player.serverLevel();

        switch (power) {
            case THUNDER_DOMAIN -> playSound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
            case GOD_SPEED -> {
                st.prevBox = null;
                st.lastHit.clear();
                playSound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.6F);
                startDash(player, st);
            }
            case WATER_CUBE -> playSound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.0F);
            case TSUNAMI -> {
                st.flood = new Flood(level, player.blockPosition(), now);
                playSound(level, player.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 0.5F);
                playSound(level, player.position(), SoundEvents.GENERIC_SPLASH, 2.0F, 0.5F);
            }
        }
        message(player, Component.translatable("msg.poseidonstrident.activated", power.displayName()));
    }

    private static void end(ServerPlayer player, PlayerPowers st, Power power, long now) {
        int i = power.ordinal();
        st.activeUntil[i] = 0;
        st.cooldownUntil[i] = now + COOLDOWN_TICKS;

        if (power == Power.GOD_SPEED) {
            st.dashTicks = 0;
            st.lastHit.clear();
            st.prevBox = null;
        } else if (power == Power.TSUNAMI && st.flood != null) {
            RESTORING.add(st.flood);   // terrain is put back over the next few ticks
            st.flood = null;
        } else if (power == Power.WATER_CUBE) {
            playSound(player.serverLevel(), player.position(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.0F);
        }
        message(player, Component.translatable("msg.poseidonstrident.ended", power.displayName()));
    }

    private static void cleanup(ServerPlayer player) {
        PlayerPowers st = STATES.remove(player.getUUID());
        if (st != null && st.flood != null) {
            RESTORING.add(st.flood);
            st.flood = null;
        }
        removePassive(player);
    }

    // ================================================================ passive powers

    private static void tickPassive(ServerPlayer player, boolean holding) {
        CompoundTag data = player.getPersistentData();
        boolean applied = data.getBoolean(TAG_PASSIVE);

        if (holding && !player.isSpectator()) {
            // Creative flight in survival (checked every tick: game-mode changes reset abilities)
            Abilities abilities = player.getAbilities();
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
                data.putBoolean(TAG_FLIGHT, true);
            }

            if (!applied || player.tickCount % 10 == 0) {
                AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
                if (health != null && !health.hasModifier(HEALTH_ID)) {
                    health.addTransientModifier(
                            new AttributeModifier(HEALTH_ID, EXTRA_HEALTH, AttributeModifier.Operation.ADD_VALUE));
                }
                ensureEffect(player, MobEffects.REGENERATION, 1);        // Regeneration II
                ensureEffect(player, MobEffects.ABSORPTION, 2);          // Absorption III
                ensureEffect(player, MobEffects.DAMAGE_BOOST, 2);        // Strength III
                ensureEffect(player, MobEffects.DAMAGE_RESISTANCE, 2);   // Resistance III
                ensureEffect(player, MobEffects.WATER_BREATHING, 0);
                ensureEffect(player, MobEffects.DOLPHINS_GRACE, 2);      // Dolphin's Grace III
                data.putBoolean(TAG_PASSIVE, true);
            }
        } else if (applied || data.getBoolean(TAG_FLIGHT)) {
            removePassive(player);
        }
    }

    private static void removePassive(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.hasModifier(HEALTH_ID)) {
            health.removeModifier(HEALTH_ID);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }

        if (data.getBoolean(TAG_PASSIVE)) {
            dropEffect(player, MobEffects.REGENERATION, 1);
            dropEffect(player, MobEffects.ABSORPTION, 2);
            dropEffect(player, MobEffects.DAMAGE_BOOST, 2);
            dropEffect(player, MobEffects.DAMAGE_RESISTANCE, 2);
            dropEffect(player, MobEffects.WATER_BREATHING, 0);
            dropEffect(player, MobEffects.DOLPHINS_GRACE, 2);
            data.remove(TAG_PASSIVE);
        }

        if (data.getBoolean(TAG_FLIGHT)) {
            data.remove(TAG_FLIGHT);
            if (!player.isCreative() && !player.isSpectator()) {
                Abilities abilities = player.getAbilities();
                abilities.mayfly = false;
                abilities.flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    /** Applies an infinite, particle-free effect unless an equal or better one is already running. */
    private static void ensureEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null
                || current.getAmplifier() < amplifier
                || (!current.isInfiniteDuration() && current.getDuration() < 600)) {
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION,
                    amplifier, false, false, true));
        }
    }

    /** Removes only effects that look like the ones this mod applied (infinite duration, same level). */
    private static void dropEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.isInfiniteDuration() && current.getAmplifier() == amplifier) {
            player.removeEffect(effect);
        }
    }

    // ================================================================ held-trident aura

    /** Beautiful particles spiralling up the trident while it is held. The theme follows the selected ability. */
    private static void tickAura(ServerPlayer player) {
        if (player.tickCount % 2 != 0 || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        PlayerPowers st = STATES.get(player.getUUID());
        int selected = st == null ? 0 : st.selected;

        boolean mainHolds = player.getMainHandItem().is(ModItems.POSEIDONS_TRIDENT.get());
        boolean rightSide = mainHolds == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);

        Vec3 look = player.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0, look.z);
        fwd = fwd.lengthSqr() < 1.0E-4 ? Vec3.ZERO : fwd.normalize();
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        double side = rightSide ? 0.45 : -0.45;

        double hx = player.getX() + right.x * side + fwd.x * 0.45;
        double hy = player.getY() + 0.9;
        double hz = player.getZ() + right.z * side + fwd.z * 0.45;

        ParticleOptions primary;
        ParticleOptions secondary;
        switch (selected) {
            case 1 -> { primary = ParticleTypes.ELECTRIC_SPARK; secondary = ParticleTypes.FLAME; }
            case 2 -> { primary = ParticleTypes.BUBBLE_POP;     secondary = ParticleTypes.END_ROD; }
            case 3 -> { primary = ParticleTypes.SPLASH;         secondary = ParticleTypes.DOLPHIN; }
            default -> { primary = ParticleTypes.ELECTRIC_SPARK; secondary = ParticleTypes.END_ROD; }
        }

        double t = player.tickCount * 0.3;
        double radius = 0.22 + 0.06 * Math.sin(t * 0.5);
        for (int k = 0; k < 2; k++) {
            double angle = t + k * Math.PI;
            double height = (player.tickCount * 0.04 + k * 0.65) % 1.3;
            double px = hx + Math.cos(angle) * radius;
            double py = hy + height;
            double pz = hz + Math.sin(angle) * radius;
            level.sendParticles(primary, px, py, pz, 1, 0.0, 0.0, 0.0, 0.01);
            if (((player.tickCount / 2) + k) % 3 == 0) {
                level.sendParticles(secondary, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        // sparkle burst at the tips every half second
        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, hx, hy + 1.45, hz, 3, 0.12, 0.12, 0.12, 0.02);
            level.sendParticles(primary, hx, hy + 1.45, hz, 4, 0.15, 0.15, 0.15, 0.05);
        }
    }

    // ================================================================ Power 1: Thunder Domain

    private static void tickThunderDomain(ServerPlayer player, ServerLevel level, long now) {
        if (now % 3 == 0) {
            // Ring of sparks marking the edge of the domain
            for (int k = 0; k < 24; k++) {
                double angle = (Math.PI * 2.0 / 24.0) * k + now * 0.05;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX() + Math.cos(angle) * THUNDER_RADIUS, player.getY() + 0.2,
                        player.getZ() + Math.sin(angle) * THUNDER_RADIUS, 1, 0.0, 0.6, 0.0, 0.05);
            }
        }

        if (now % THUNDER_INTERVAL != 0) return;

        int struck = 0;
        for (LivingEntity target : targetsAround(player, level, THUNDER_RADIUS)) {
            if (struck++ >= THUNDER_MAX_TARGETS) break;
            strike(level, target.position());
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().indirectMagic(player, player), THUNDER_DAMAGE);
        }
    }

    // ================================================================ Power 2: Zenitsu's God Speed

    private static void startDash(ServerPlayer player, PlayerPowers st) {
        st.dashDir = player.getLookAngle().normalize();
        st.dashTicks = DASH_TICKS;
        playSound(player.serverLevel(), player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.7F);
    }

    private static void tickGodSpeed(ServerPlayer player, PlayerPowers st, ServerLevel level, long now) {
        if (now % 10 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, SPEED_AMPLIFIER, false, false, true));
        }

        // Burst dash
        boolean dashing = st.dashTicks > 0;
        if (dashing) {
            player.setDeltaMovement(st.dashDir.scale(DASH_SPEED));
            player.hurtMarked = true;   // sync the velocity to the client
            player.fallDistance = 0.0F;
            st.dashTicks--;
        }

        // Trail
        if (dashing || player.isSprinting()) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.3, 0.6, 0.3, 0.2);
        }

        // Collision damage along the path travelled this tick
        AABB box = player.getBoundingBox();
        AABB sweep = st.prevBox == null ? box : box.minmax(st.prevBox);
        st.prevBox = box;

        if (dashing || player.isSprinting()) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep.inflate(1.0, 0.5, 1.0),
                    e -> isValidTarget(player, e))) {
                Long last = st.lastHit.get(target.getUUID());
                if (last != null && now - last < 20) continue;
                st.lastHit.put(target.getUUID(), now);

                strike(level, target.position());
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().playerAttack(player), DASH_DAMAGE);
            }
        }
        if (now % 200 == 0) {
            st.lastHit.values().removeIf(t -> now - t > 100);
        }
    }

    private static boolean isCharged(PlayerPowers st, long now) {
        return st.chargeStart >= 0
                && now >= st.chargeStart + CHARGE_TICKS
                && now <= st.chargeStart + CHARGE_TICKS + CHARGE_WINDOW;
    }

    // ================================================================ Power 3: Absolute Water Cube

    private static void tickWaterCube(ServerPlayer player, ServerLevel level, long now) {
        player.clearFire();

        AABB cube = player.getBoundingBox().inflate(CUBE_HALF, CUBE_HALF, CUBE_HALF);

        // Keep enemies out of the cube
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, cube, e -> isValidTarget(player, e))) {
            Vec3 away = target.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
            target.setDeltaMovement(flat.x * 0.8, Math.max(target.getDeltaMovement().y, 0.25), flat.z * 0.8);
            target.hurtMarked = true;
        }

        // Stop incoming projectiles (arrows, tridents, fireballs ...)
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, cube, p -> p.getOwner() != player)) {
            level.sendParticles(ParticleTypes.SPLASH, projectile.getX(), projectile.getY(), projectile.getZ(),
                    8, 0.2, 0.2, 0.2, 0.1);
            projectile.discard();
        }

        // Visuals: glowing edges + falling water on the faces
        if (now % 3 == 0) {
            drawCube(level, player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ(), CUBE_HALF);
        }
    }

    private static void drawCube(ServerLevel level, double cx, double cy, double cz, double h) {
        double step = 0.8;
        for (double t = -h; t <= h + 1.0E-6; t += step) {
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    // edges parallel to X, Y and Z
                    particle(level, ParticleTypes.SPLASH, cx + t, cy + a * h, cz + b * h);
                    particle(level, ParticleTypes.SPLASH, cx + a * h, cy + t, cz + b * h);
                    particle(level, ParticleTypes.SPLASH, cx + a * h, cy + b * h, cz + t);
                }
            }
        }
        var random = level.getRandom();
        for (int k = 0; k < 14; k++) {
            double u = (random.nextDouble() * 2 - 1) * h;
            double v = (random.nextDouble() * 2 - 1) * h;
            double side = random.nextBoolean() ? h : -h;
            switch (random.nextInt(3)) {
                case 0 -> particle(level, ParticleTypes.FALLING_WATER, cx + side, cy + u, cz + v);
                case 1 -> particle(level, ParticleTypes.FALLING_WATER, cx + u, cy + side, cz + v);
                default -> particle(level, ParticleTypes.DOLPHIN, cx + u, cy + v, cz + side);
            }
        }
    }

    private static void particle(ServerLevel level, ParticleOptions type, double x, double y, double z) {
        level.sendParticles(type, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // ================================================================ Power 4: The Great Tsunami

    private static void tickTsunami(ServerPlayer player, PlayerPowers st, ServerLevel level, long now) {
        Flood flood = st.flood;
        if (flood == null) return;

        int before = flood.builtRadius;
        int radius = flood.advance(level, now);

        // Wave front particles while the flood is still spreading
        if (radius > before && radius < Flood.RADIUS) {
            for (int d = -radius; d <= radius; d += 4) {
                for (int s = -1; s <= 1; s += 2) {
                    wave(level, flood.centerX + d, player.getY(), flood.centerZ + s * radius);
                    wave(level, flood.centerX + s * radius, player.getY(), flood.centerZ + d);
                }
            }
        }

        // Everything standing in the flood is swept outward and drowns
        if (now % 10 == 0) {
            AABB area = new AABB(flood.centerX - Flood.RADIUS, player.getY() - 40, flood.centerZ - Flood.RADIUS,
                    flood.centerX + Flood.RADIUS + 1, player.getY() + 40, flood.centerZ + Flood.RADIUS + 1);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> isValidTarget(player, e))) {
                if (!target.isInWater()) continue;

                Vec3 away = target.position().subtract(flood.centerX + 0.5, target.getY(), flood.centerZ + 0.5);
                Vec3 flat = new Vec3(away.x, 0.0, away.z);
                if (flat.lengthSqr() > 1.0E-4) {
                    flat = flat.normalize().scale(0.35);
                    target.setDeltaMovement(target.getDeltaMovement().add(flat.x, 0.0, flat.z));
                    target.hurtMarked = true;
                }
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().drown(), TSUNAMI_DROWN_DAMAGE);
            }
        }
    }

    private static void wave(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.SPLASH, x + 0.5, y + 0.5, z + 0.5, 10, 1.0, 0.8, 1.0, 0.3);
        level.sendParticles(ParticleTypes.CLOUD, x + 0.5, y + 1.0, z + 0.5, 2, 0.8, 0.3, 0.8, 0.05);
    }

    // ================================================================ helpers

    public static boolean isHolding(Player player) {
        return player.getMainHandItem().is(ModItems.POSEIDONS_TRIDENT.get())
                || player.getOffhandItem().is(ModItems.POSEIDONS_TRIDENT.get());
    }

    /** Anything living that is not the owner, the owner's pets, or a creative/spectator player. */
    private static boolean isValidTarget(ServerPlayer owner, LivingEntity entity) {
        if (entity == owner || !entity.isAlive() || entity instanceof ArmorStand) return false;
        if (entity instanceof Player other && (other.isCreative() || other.isSpectator())) return false;
        if (entity instanceof TamableAnimal pet && pet.isOwnedBy(owner)) return false;
        return true;
    }

    private static List<LivingEntity> targetsAround(ServerPlayer player, ServerLevel level, double radius) {
        double r2 = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> isValidTarget(player, e) && e.distanceToSqr(player) <= r2);
    }

    private static void strike(ServerLevel level, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(pos);
            bolt.setVisualOnly(true);   // damage is dealt by us, so no fires and no friendly fire
            level.addFreshEntity(bolt);
        }
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void message(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }
}
