package de.Z7534.ztone.claims.listener;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.claims.config.ConfigManager;
import de.Z7534.ztone.claims.manager.BypassManager;
import de.Z7534.ztone.claims.manager.ClaimManager;
import de.Z7534.ztone.claims.manager.PvPClaimBypassManager;
import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ClaimPermission;
import de.Z7534.ztone.claims.model.PlayerData;
import de.Z7534.ztone.claims.util.Text;
import de.Z7534.ztone.pvpmanager.Pvpmanager;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.LightningStrikeEvent.Cause;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;

public final class ProtectionListener implements Listener {
   private final ClaimManager claimManager;
   private final ConfigManager config;
   private final BypassManager bypass;
   private final PvPClaimBypassManager pvpClaimBypass;
   private final Map<String, Long> recentMonsterVictims = new ConcurrentHashMap();

   public ProtectionListener(ClaimManager claimManager, ConfigManager config, BypassManager bypass, PvPClaimBypassManager pvpClaimBypass) {
      this.claimManager = claimManager;
      this.config = config;
      this.bypass = bypass;
      this.pvpClaimBypass = pvpClaimBypass;
   }

   private boolean allowed(Player player, Location loc, ClaimPermission permission) {
      if (this.bypass.isActive(player)) {
         return true;
      } else {
         Claim claim = this.claimManager.getClaimAt(loc);
         if (claim == null) {
            return true;
         } else if (claim.isBanned(player.getUniqueId())) {
            return false;
         } else if (permission == ClaimPermission.PVP) {
            return this.pvpClaimBypass != null && this.pvpClaimBypass.isClaimBypassed(claim.id());
         } else if (permission == ClaimPermission.SIT && player.hasPermission(BypassManager.PERMISSION_BYPASS_SIT)) {
            return true;
         } else {
            return claim.hasPermission(player.getUniqueId(), permission);
         }
      }
   }

   private boolean allowsExplosions(Location loc) {
      Claim claim = this.claimManager.getClaimAt(loc);
      return claim == null ? true : (Boolean)this.claimManager.findPlayerData(claim.owner()).map(PlayerData::explosionsEnabled).orElse(false);
   }

   private void deny(Player player) {
      player.sendMessage(Text.line(this.config.prefix() + this.config.message("action-denied")));
   }

   private void deny(Player player, EquipmentSlot hand) {
      if (hand == EquipmentSlot.HAND) {
         this.deny(player);
      }

   }

   private boolean crossesClaimBorder(Block block, BlockFace direction) {
      Claim from = this.claimManager.getClaimAt(block.getLocation());
      Claim to = this.claimManager.getClaimAt(block.getRelative(direction).getLocation());
      return from != to;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onLiquidFlow(BlockFromToEvent e) {
      Claim from = this.claimManager.getClaimAt(e.getBlock().getLocation());
      Claim to = this.claimManager.getClaimAt(e.getToBlock().getLocation());
      if (from != to) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPistonExtend(BlockPistonExtendEvent e) {
      Block piston = e.getBlock();
      Claim pistonClaim = this.claimManager.getClaimAt(piston.getLocation());
      BlockFace direction = e.getDirection();
      Claim headClaim = this.claimManager.getClaimAt(piston.getRelative(direction).getLocation());
      if (pistonClaim != headClaim) {
         e.setCancelled(true);
      } else {
         for(Block moved : e.getBlocks()) {
            Claim movedClaim = this.claimManager.getClaimAt(moved.getLocation());
            Claim destClaim = this.claimManager.getClaimAt(moved.getRelative(direction).getLocation());
            if (movedClaim != pistonClaim || destClaim != pistonClaim || movedClaim != destClaim) {
               e.setCancelled(true);
               return;
            }
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPistonRetract(BlockPistonRetractEvent e) {
      Block piston = e.getBlock();
      Claim pistonClaim = this.claimManager.getClaimAt(piston.getLocation());
      BlockFace facing = e.getDirection();
      Claim headClaim = this.claimManager.getClaimAt(piston.getRelative(facing).getLocation());
      if (pistonClaim != headClaim) {
         e.setCancelled(true);
      } else if (e.isSticky()) {
         Block attached = piston.getRelative(facing, 2);
         Claim attachedClaim = this.claimManager.getClaimAt(attached.getLocation());
         if (attachedClaim != pistonClaim && attached.getType() != Material.AIR) {
            e.setCancelled(true);
         } else {
            for(Block moved : e.getBlocks()) {
               Claim movedClaim = this.claimManager.getClaimAt(moved.getLocation());
               Claim dest1 = this.claimManager.getClaimAt(moved.getRelative(facing).getLocation());
               Claim dest2 = this.claimManager.getClaimAt(moved.getRelative(facing.getOppositeFace()).getLocation());
               if (movedClaim != pistonClaim || dest1 != pistonClaim || dest2 != pistonClaim) {
                  e.setCancelled(true);
                  return;
               }
            }

         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent e) {
      if (e.getBlock().getType() == Material.SPAWNER && !this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.SPAWNER)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      } else {
         if (!this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            this.deny(e.getPlayer());
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent e) {
      if (e.getBlock().getType() == Material.SPAWNER && !this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.SPAWNER)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      } else {
         if (!this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            this.deny(e.getPlayer());
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onHangingBreakGeneric(HangingBreakEvent e) {
      if (e.getCause() == RemoveCause.EXPLOSION && !this.allowsExplosions(e.getEntity().getLocation())) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onHangingBreak(HangingBreakByEntityEvent e) {
      Entity remover = e.getRemover();
      if ((remover instanceof Explosive || remover instanceof TNTPrimed || remover instanceof Creeper) && !this.allowsExplosions(e.getEntity().getLocation())) {
         e.setCancelled(true);
      } else {
         Player player = this.resolvePlayer(remover);
         if (player != null) {
            if (!this.allowed(player, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, e.getEntity().getLocation(), ClaimPermission.BUILD)) {
               e.setCancelled(true);
               this.deny(player);
            }

         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onHangingPlace(HangingPlaceEvent e) {
      Player player = e.getPlayer();
      if (player != null) {
         if (!this.allowed(player, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, e.getEntity().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            this.deny(player);
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBucketEmpty(PlayerBucketEmptyEvent e) {
      if (!this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUCKETS)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onBucketFill(PlayerBucketFillEvent e) {
      if (!this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.BUCKETS)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent e) {
      Player player = e.getPlayer();
      if (e.getAction() == Action.PHYSICAL) {
         Block trodden = e.getClickedBlock();
         if (trodden != null) {
            Material troddenType = trodden.getType();
            if (troddenType == Material.FARMLAND) {
               Claim claim = this.claimManager.getClaimAt(trodden.getLocation());
               if (claim != null && !this.bypass.isActive(player) && !claim.isOwner(player.getUniqueId())) {
                  e.setCancelled(true);
               }

            } else {
               if ((troddenType.name().endsWith("_PRESSURE_PLATE") || troddenType == Material.TRIPWIRE) && !this.allowed(player, trodden.getLocation(), ClaimPermission.REDSTONE)) {
                  e.setCancelled(true);
               }

            }
         }
      } else {
         if ((e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) && e.getItem() != null) {
            Material held = e.getItem().getType();
            String name = held.name();
            if ((held == Material.ENDER_PEARL || name.contains("WIND_CHARGE")) && !this.allowed(player, player.getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, player.getLocation(), ClaimPermission.BUILD)) {
               e.setCancelled(true);
               this.deny(player);
               return;
            }

            if (held == Material.ARMOR_STAND || held == Material.ITEM_FRAME || held == Material.GLOW_ITEM_FRAME || held == Material.PAINTING || name.endsWith("_SPAWN_EGG")) {
               Location targetLoc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : player.getLocation();
               if (!this.allowed(player, targetLoc, ClaimPermission.ENTITY_INTERACT)) {
                  e.setCancelled(true);
                  this.deny(player);
                  return;
               }
            }

            if (name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT") || name.endsWith("_RAFT") || name.endsWith("_MINECART")) {
               Location targetLoc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : player.getLocation();
               if (!this.allowed(player, targetLoc, ClaimPermission.BUILD) && !this.allowed(player, targetLoc, ClaimPermission.VEHICLES)) {
                  e.setCancelled(true);
                  this.deny(player);
                  return;
               }
            }

            if (held == Material.END_CRYSTAL) {
               Location targetLoc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : player.getLocation();
               if (!this.allowed(player, targetLoc, ClaimPermission.FIRE_EXPLOSIONS) && !this.allowed(player, targetLoc, ClaimPermission.ENTITY_INTERACT)) {
                  e.setCancelled(true);
                  this.deny(player);
                  return;
               }
            }
         }

         if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = e.getClickedBlock();
            if (block != null) {
               Material type = block.getType();
               EquipmentSlot hand = e.getHand();
               if (type == Material.SPAWNER && !this.allowed(player, block.getLocation(), ClaimPermission.SPAWNER)) {
                  e.setCancelled(true);
                  this.deny(player, hand);
               } else if (e.getItem() != null && (e.getItem().getType() == Material.SPAWNER || e.getItem().getType().name().endsWith("_SPAWN_EGG")) && type == Material.SPAWNER && !this.allowed(player, block.getLocation(), ClaimPermission.SPAWNER)) {
                  e.setCancelled(true);
                  this.deny(player, hand);
               } else if (type != Material.BARREL && !(block.getState() instanceof Container) && !(block.getState() instanceof BlockInventoryHolder) && !this.isShelf(type) && !this.isWorkbenchLike(type)) {
                  if (block.getBlockData() instanceof Openable) {
                     if (!this.allowed(player, block.getLocation(), ClaimPermission.DOORS)) {
                        e.setCancelled(true);
                        this.deny(player, hand);
                     }

                  } else if (type != Material.LEVER && !type.name().endsWith("_BUTTON") && !type.name().endsWith("_PRESSURE_PLATE")) {
                     if (type != Material.BELL && !type.name().endsWith("_NOTE_BLOCK") && type != Material.NOTE_BLOCK && !type.name().endsWith("_JUKEBOX") && type != Material.JUKEBOX) {
                        if (type == Material.TNT) {
                           if (!this.allowed(player, block.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                              e.setCancelled(true);
                              this.deny(player, hand);
                           }

                        } else if (type == Material.RESPAWN_ANCHOR && !this.allowed(player, block.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                           e.setCancelled(true);
                           this.deny(player, hand);
                        } else {
                           if (type.name().endsWith("_BED")) {
                              if (block.getWorld().getEnvironment() != Environment.NORMAL) {
                                 if (!this.allowed(player, block.getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
                                    e.setCancelled(true);
                                    this.deny(player, hand);
                                    return;
                                 }
                              } else if (!this.allowed(player, block.getLocation(), ClaimPermission.DOORS)) {
                                 e.setCancelled(true);
                                 this.deny(player, hand);
                                 return;
                              }
                           }

                           if ((this.isShelf(type) || type == Material.DECORATED_POT || type.name().endsWith("CAULDRON") || type == Material.COMPOSTER) && !this.allowed(player, block.getLocation(), ClaimPermission.CONTAINERS)) {
                              e.setCancelled(true);
                              this.deny(player, hand);
                           } else {
                              if ((type == Material.SWEET_BERRY_BUSH || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) && !this.allowed(player, block.getLocation(), ClaimPermission.BUILD)) {
                                 e.setCancelled(true);
                                 this.deny(player, hand);
                              }

                           }
                        }
                     } else {
                        if (!this.allowed(player, block.getLocation(), ClaimPermission.BELLS_NOTEBLOCKS)) {
                           e.setCancelled(true);
                           this.deny(player, hand);
                        }

                     }
                  } else {
                     if (!this.allowed(player, block.getLocation(), ClaimPermission.REDSTONE)) {
                        e.setCancelled(true);
                        this.deny(player, hand);
                     }

                  }
               } else if (Ztone.getInstance().getDeathchest() == null || !Ztone.getInstance().getDeathchest().getDeathChestManager().isDeathChest(block)) {
                  if (!this.allowed(player, block.getLocation(), ClaimPermission.CONTAINERS)) {
                     e.setCancelled(true);
                     this.deny(player, hand);
                  }

               }
            }
         }
      }
   }

   private boolean isShelf(Material type) {
      if (type == null) {
         return false;
      } else {
         String name = type.name();
         return name.endsWith("_SHELF") || name.equals("CHISELED_BOOKSHELF") || name.contains("SHELF") && !name.equals("BOOKSHELF") && !name.equals("LEGACY_BOOKSHELF");
      }
   }

   private boolean isWorkbenchLike(Material type) {
        return switch (type) {
            case CRAFTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, ENCHANTING_TABLE, BREWING_STAND,
                    LOOM, CARTOGRAPHY_TABLE, GRINDSTONE, SMITHING_TABLE, STONECUTTER, BEACON, LECTERN -> true;
            default -> false;
        };
    }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onInventoryMoveItem(InventoryMoveItemEvent e) {
      Location src = e.getSource().getLocation();
      Location dest = e.getDestination().getLocation();
      if (src != null && dest != null) {
         Claim srcClaim = this.claimManager.getClaimAt(src);
         Claim destClaim = this.claimManager.getClaimAt(dest);
         if (srcClaim != destClaim) {
            e.setCancelled(true);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onTakeLecternBook(PlayerTakeLecternBookEvent e) {
      if (!this.allowed(e.getPlayer(), e.getLectern().getLocation(), ClaimPermission.CONTAINERS)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onInsertLecternBook(PlayerInsertLecternBookEvent e) {
      if (!this.allowed(e.getPlayer(), e.getLectern().getLocation(), ClaimPermission.CONTAINERS)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onIgnite(BlockIgniteEvent e) {
      if (e.getPlayer() != null) {
         if (!this.allowed(e.getPlayer(), e.getBlock().getLocation(), ClaimPermission.FIRE_EXPLOSIONS)) {
            e.setCancelled(true);
            this.deny(e.getPlayer());
         }

      } else {
         Claim claim = this.claimManager.getClaimAt(e.getBlock().getLocation());
         if (claim != null && (e.getBlock().getType() == Material.TNT || !this.allowsExplosions(e.getBlock().getLocation()))) {
            e.setCancelled(true);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockBurn(BlockBurnEvent e) {
      Claim claim = this.claimManager.getClaimAt(e.getBlock().getLocation());
      if (claim != null) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockSpread(BlockSpreadEvent e) {
      if (e.getSource().getType() == Material.FIRE) {
         Claim toClaim = this.claimManager.getClaimAt(e.getBlock().getLocation());
         Claim fromClaim = this.claimManager.getClaimAt(e.getSource().getLocation());
         if (toClaim != null && toClaim != fromClaim) {
            e.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDispense(BlockDispenseEvent e) {
      BlockData var3 = e.getBlock().getBlockData();
      if (var3 instanceof Dispenser dispData) {
         BlockFace var8 = dispData.getFacing();
         Block targetBlock = e.getBlock().getRelative(var8);
         Claim fromClaim = this.claimManager.getClaimAt(e.getBlock().getLocation());
         Claim toClaim = this.claimManager.getClaimAt(targetBlock.getLocation());
         if (toClaim != null && toClaim != fromClaim) {
            Material item = e.getItem().getType();
            if (item == Material.TNT || item == Material.LAVA_BUCKET || item == Material.WATER_BUCKET || item == Material.FIRE_CHARGE || item == Material.FLINT_AND_STEEL || item.name().endsWith("_MINECART") || item.name().endsWith("_BOAT") || item.name().endsWith("_CHEST_BOAT") || item.name().endsWith("_RAFT")) {
               e.setCancelled(true);
            }
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityChangeBlock(EntityChangeBlockEvent e) {
      Claim claim = this.claimManager.getClaimAt(e.getBlock().getLocation());
      if (claim != null) {
         Entity entity = e.getEntity();
         if (!(entity instanceof Enderman) && !(entity instanceof Wither) && !(entity instanceof WitherSkull) && !(entity instanceof Ravager) && !(entity instanceof Silverfish)) {
            if (entity instanceof FallingBlock) {
               Claim from = this.claimManager.getClaimAt(entity.getLocation());
               if (from != claim) {
                  e.setCancelled(true);
                  entity.remove();
                  return;
               }
            }

            if (entity instanceof Player) {
               Player player = (Player)entity;
               if (!this.allowed(player, e.getBlock().getLocation(), ClaimPermission.BUILD)) {
                  e.setCancelled(true);
               }
            }

         } else {
            e.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onProjectileLaunch(ProjectileLaunchEvent e) {
      ProjectileSource var3 = e.getEntity().getShooter();
      if (var3 instanceof Player player) {
         if ((e.getEntity() instanceof EnderPearl || e.getEntity() instanceof AbstractWindCharge) && !this.allowed(player, player.getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, player.getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            this.deny(player);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onProjectileHit(ProjectileHitEvent e) {
      Player shooter = this.resolvePlayer(e.getEntity());
      Entity hitEntity = e.getHitEntity();
      Block hitBlock = e.getHitBlock();
      if (e.getEntity() instanceof AbstractWindCharge) {
         Location loc = hitEntity != null ? hitEntity.getLocation() : (hitBlock != null ? hitBlock.getLocation() : e.getEntity().getLocation());
         if (shooter != null && !this.allowed(shooter, loc, ClaimPermission.ENTITY_INTERACT) && !this.allowed(shooter, loc, ClaimPermission.BUILD)) {
            e.setCancelled(true);
            e.getEntity().remove();
            this.deny(shooter);
            return;
         }
      }

      if (e.getEntity() instanceof EnderPearl) {
         Location loc = hitEntity != null ? hitEntity.getLocation() : (hitBlock != null ? hitBlock.getLocation() : e.getEntity().getLocation());
         if (shooter != null && !this.allowed(shooter, loc, ClaimPermission.ENTITY_INTERACT) && !this.allowed(shooter, loc, ClaimPermission.BUILD)) {
            e.setCancelled(true);
            e.getEntity().remove();
            this.deny(shooter);
            return;
         }
      }

      if (hitEntity != null && shooter != null && (hitEntity instanceof ArmorStand || hitEntity instanceof ItemFrame || hitEntity instanceof Painting || hitEntity instanceof Hanging || hitEntity instanceof EnderCrystal) && !this.allowed(shooter, hitEntity.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         e.setCancelled(true);
         e.getEntity().remove();
         this.deny(shooter);
      } else if (hitBlock != null) {
         if (e.getEntity().getFireTicks() > 0 || e.getEntity() instanceof Fireball || e.getEntity() instanceof SmallFireball) {
            if (hitBlock.getType() == Material.TNT && (shooter == null || !this.allowed(shooter, hitBlock.getLocation(), ClaimPermission.FIRE_EXPLOSIONS))) {
               e.setCancelled(true);
               e.getEntity().remove();
               return;
            }

            if (shooter != null && !this.allowed(shooter, hitBlock.getLocation(), ClaimPermission.BUILD)) {
               e.setCancelled(true);
               e.getEntity().remove();
            }
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPotionSplash(PotionSplashEvent e) {
      Player shooter = null;
      ProjectileSource var4 = e.getEntity().getShooter();
      if (var4 instanceof Player p) {
         shooter = p;
      }

      if (shooter != null) {
         for(LivingEntity affected : e.getAffectedEntities()) {
            if (!(affected instanceof Player) && !(affected instanceof Monster) && !this.allowed(shooter, affected.getLocation(), ClaimPermission.ATTACK_MOBS) && !this.allowed(shooter, affected.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
               e.setIntensity(affected, (double)0.0F);
            }
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onLightning(LightningStrikeEvent e) {
      Claim claim = this.claimManager.getClaimAt(e.getLightning().getLocation());
      if (claim != null && !this.allowsExplosions(e.getLightning().getLocation()) && e.getCause() == Cause.TRIDENT) {
         e.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent e) {
      Player player = e.getPlayer();
      PlayerTeleportEvent.TeleportCause cause = e.getCause();
      if (cause != TeleportCause.ENDER_PEARL || (this.allowed(player, e.getTo(), ClaimPermission.ENTITY_INTERACT) || this.allowed(player, e.getTo(), ClaimPermission.BUILD)) && (this.allowed(player, e.getFrom(), ClaimPermission.ENTITY_INTERACT) || this.allowed(player, e.getFrom(), ClaimPermission.BUILD))) {
         String causeName = cause.name();
         if ("CHORUS_FRUIT".equals(causeName) || "CONSUMABLE_EFFECT".equals(causeName)) {
            Claim to = this.claimManager.getClaimAt(e.getTo());
            if (to != null && (to.isBanned(player.getUniqueId()) || Ztone.getInstance().isDragonEggBlockingClaim(player) || !this.allowed(player, e.getTo(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, e.getTo(), ClaimPermission.BUILD))) {
               e.setCancelled(true);
               this.deny(player);
            }
         }

      } else {
         e.setCancelled(true);
         this.deny(player);
      }
   }

   private final Map<UUID, Boolean> droppingOnInventoryClose = new ConcurrentHashMap<>();

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onInventoryClickDrop(InventoryClickEvent e) {
      if (!(e.getWhoClicked() instanceof Player player)) {
         return;
      }
      InventoryAction action = e.getAction();
      ClickType click = e.getClick();
      boolean isDropAction = action == InventoryAction.DROP_ALL_CURSOR
            || action == InventoryAction.DROP_ONE_CURSOR
            || action == InventoryAction.DROP_ALL_SLOT
            || action == InventoryAction.DROP_ONE_SLOT
            || click == ClickType.DROP
            || click == ClickType.CONTROL_DROP;
      if (isDropAction && !this.allowed(player, player.getLocation(), ClaimPermission.ITEMS)) {
         e.setCancelled(true);
         this.deny(player);
         player.updateInventory();
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onInventoryCloseCheck(InventoryCloseEvent e) {
      if (!(e.getPlayer() instanceof Player player)) {
         return;
      }
      if (!this.allowed(player, player.getLocation(), ClaimPermission.ITEMS)) {
         this.droppingOnInventoryClose.put(player.getUniqueId(), Boolean.TRUE);
         org.bukkit.Bukkit.getScheduler().runTask(Ztone.getInstance(), () -> {
            this.droppingOnInventoryClose.remove(player.getUniqueId());
         });
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent e) {
      Player player = e.getPlayer();
      if (!this.allowed(player, player.getLocation(), ClaimPermission.ITEMS)) {
         e.setCancelled(true);
         this.deny(player);

         // Falls das Item gedroppt wurde, weil das eigene Inventar voll ist (z.B. aus einem Container
         // oder beim Schließen eines Inventars mit aktivem Cursor-Item), verwirft Bukkit/Vanilla
         // bei e.setCancelled(true) den Überschuss von player.getInventory().addItem(...) einfach!
         // Wir fangen das ab und legen den nicht ins Spielerinventar passenden Rest zurück in den
         // geöffneten Container (oder lassen den Drop notfalls zu, damit niemals ein Item gelöscht wird).
         Inventory topInv = player.getOpenInventory().getTopInventory();
         boolean closing = Boolean.TRUE.equals(this.droppingOnInventoryClose.get(player.getUniqueId()));
         if (!closing && topInv != null && topInv.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            ItemStack droppedStack = e.getItemDrop().getItemStack().clone();
            if (!this.canFitInInventory(player.getInventory(), droppedStack)) {
               ItemStack toPlayer = droppedStack.clone();
               Map<Integer, ItemStack> leftover = player.getInventory().addItem(toPlayer);
               for (ItemStack rest : leftover.values()) {
                  Map<Integer, ItemStack> containerLeftover = topInv.addItem(rest);
                  for (ItemStack stillLeft : containerLeftover.values()) {
                     player.getWorld().dropItemNaturally(player.getLocation(), stillLeft);
                  }
               }
               e.getItemDrop().setItemStack(new ItemStack(Material.AIR));
               player.updateInventory();
               return;
            }
         } else if (closing) {
            ItemStack droppedStack = e.getItemDrop().getItemStack().clone();
            if (!this.canFitInInventory(player.getInventory(), droppedStack)) {
               if (topInv != null && topInv.getHolder() instanceof BlockInventoryHolder && topInv.getLocation() != null) {
                  ItemStack toPlayer = droppedStack.clone();
                  Map<Integer, ItemStack> leftover = player.getInventory().addItem(toPlayer);
                  for (ItemStack rest : leftover.values()) {
                     Map<Integer, ItemStack> containerLeftover = topInv.addItem(rest);
                     for (ItemStack stillLeft : containerLeftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), stillLeft);
                     }
                  }
                  e.getItemDrop().setItemStack(new ItemStack(Material.AIR));
                  player.updateInventory();
                  return;
               }
               // Kein physischer Container (z.B. Crafting-Table): Drop zulassen statt Item zu vernichten
               e.setCancelled(false);
               return;
            }
         }
         player.updateInventory();
      }
   }

   private boolean canFitInInventory(org.bukkit.inventory.PlayerInventory inv, ItemStack stack) {
      if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
         return true;
      }
      int remaining = stack.getAmount();
      int maxStack = stack.getMaxStackSize();
      for (ItemStack content : inv.getStorageContents()) {
         if (content == null || content.getType() == Material.AIR) {
            return true;
         }
         if (content.isSimilar(stack) && content.getAmount() < maxStack) {
            remaining -= (maxStack - content.getAmount());
            if (remaining <= 0) {
               return true;
            }
         }
      }
      return false;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPickup(EntityPickupItemEvent e) {
      LivingEntity var3 = e.getEntity();
      if (var3 instanceof Player player) {
         if (!this.allowed(player, player.getLocation(), ClaimPermission.ITEMS)) {
            e.setCancelled(true);
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent e) {
      Entity source = e.getEntity();
      if (source instanceof Player victim) {
         source = e.getDamager();
         if (source instanceof Projectile proj) {
            ProjectileSource var6 = proj.getShooter();
            if (var6 instanceof Entity shooter) {
               source = shooter;
            }
         }

         if (source instanceof Monster monster) {
            this.recentMonsterVictims.put(monster.getUniqueId() + ":" + victim.getUniqueId(), System.currentTimeMillis());
         }

         if ((source instanceof Creeper || source instanceof TNTPrimed || source instanceof Explosive || e.getCause() == DamageCause.ENTITY_EXPLOSION) && !this.allowsExplosions(victim.getLocation())) {
            e.setCancelled(true);
            return;
         }
      }

      Player attacker = this.resolvePlayer(e.getDamager());
      if (e.getDamager() instanceof AbstractWindCharge) {
         if (attacker != null) {
            Entity pvpMgr = e.getEntity();
            if (pvpMgr instanceof Player) {
               Player victim = (Player)pvpMgr;
               if (attacker.getUniqueId().equals(victim.getUniqueId()) && (this.allowed(attacker, victim.getLocation(), ClaimPermission.ENTITY_INTERACT) || this.allowed(attacker, victim.getLocation(), ClaimPermission.BUILD))) {
                  return;
               }
            }
         }

         if (attacker != null && !this.allowed(attacker, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(attacker, e.getEntity().getLocation(), ClaimPermission.BUILD)) {
            e.setCancelled(true);
            return;
         }
      }

      Entity victimEntity = e.getEntity();
      if (victimEntity instanceof EnderCrystal crystal) {
         if (attacker == null || !this.allowed(attacker, crystal.getLocation(), ClaimPermission.FIRE_EXPLOSIONS) && !this.allowed(attacker, crystal.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
            e.setCancelled(true);
            if (attacker != null) {
               this.deny(attacker);
            }

            return;
         }
      }

      if (!(e.getEntity() instanceof ArmorStand) && !(e.getEntity() instanceof ItemFrame) && !(e.getEntity() instanceof Painting) && !(e.getEntity() instanceof Hanging) || attacker != null && this.allowed(attacker, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         victimEntity = e.getEntity();
         if (victimEntity instanceof Vehicle) {
            Vehicle vehicle = (Vehicle)victimEntity;
            if (attacker == null || !this.allowed(attacker, vehicle.getLocation(), ClaimPermission.BUILD) && !this.allowed(attacker, vehicle.getLocation(), ClaimPermission.VEHICLES)) {
               e.setCancelled(true);
               if (attacker != null) {
                  this.deny(attacker);
               }
            }

         } else if (attacker == null) {
            if ((e.getCause() == DamageCause.ENTITY_EXPLOSION || e.getDamager() instanceof Creeper) && !this.allowsExplosions(e.getEntity().getLocation())) {
               e.setCancelled(true);
            }

         } else {
            victimEntity = e.getEntity();
            if (victimEntity instanceof Player) {
               Player victim = (Player)victimEntity;
               if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
                  if (this.pvpClaimBypass == null || !this.pvpClaimBypass.isPlayerBypassed(attacker) && !this.pvpClaimBypass.isPlayerBypassed(victim)) {
                     Pvpmanager pvpMgr = Ztone.getInstance().getPvpManager();
                     if (pvpMgr == null || !pvpMgr.getOverrideManager().isOverriding(attacker.getUniqueId())) {
                        if (!this.bypass.isActive(attacker)) {
                           Claim victimClaim = this.claimManager.getClaimAt(victim.getLocation());
                           Claim attackerClaim = this.claimManager.getClaimAt(attacker.getLocation());
                           boolean victimInProtectedClaim = victimClaim != null && (this.pvpClaimBypass == null || !this.pvpClaimBypass.isClaimBypassed(victimClaim.id()));
                           boolean attackerInProtectedClaim = attackerClaim != null && (this.pvpClaimBypass == null || !this.pvpClaimBypass.isClaimBypassed(attackerClaim.id()));
                           if (victimInProtectedClaim || attackerInProtectedClaim) {
                              e.setCancelled(true);
                              this.deny(attacker);
                           }
                        }
                     }
                  }
               }
            } else {
               victimEntity = e.getEntity();
               if (victimEntity instanceof Monster) {
                  Monster monster = (Monster)victimEntity;
                  if (!this.isRetaliating(attacker, monster)) {
                     if (!this.allowed(attacker, monster.getLocation(), ClaimPermission.ATTACK_MOBS)) {
                        e.setCancelled(true);
                        this.deny(attacker);
                     }

                  }
               } else {
                  if (e.getEntity() instanceof LivingEntity) {
                     victimEntity = e.getEntity();
                     if (victimEntity instanceof Tameable) {
                        Tameable tameable = (Tameable)victimEntity;
                        if (tameable.isTamed() && tameable.getOwner() != null) {
                           UUID ownerUuid = tameable.getOwner().getUniqueId();
                           if (!attacker.getUniqueId().equals(ownerUuid)) {
                              Pvpmanager pvpManager = Ztone.getInstance().getPvpManager();
                              if (pvpManager != null && (!pvpManager.canPvP(ownerUuid) || !pvpManager.canPvP(attacker))) {
                                 e.setCancelled(true);
                                 this.deny(attacker);
                                 return;
                              }
                           }
                        }
                     }

                     if (!this.allowed(attacker, e.getEntity().getLocation(), ClaimPermission.ATTACK_MOBS)) {
                        e.setCancelled(true);
                        this.deny(attacker);
                     }
                  }

               }
            }
         }
      } else {
         e.setCancelled(true);
         if (attacker != null) {
            this.deny(attacker);
         }

      }
   }

   private boolean isRetaliating(Player attacker, Monster monster) {
      if (monster.getTarget() != null && monster.getTarget().getUniqueId().equals(attacker.getUniqueId())) {
         return true;
      } else {
         Long attackedAt = (Long)this.recentMonsterVictims.get(monster.getUniqueId() + ":" + attacker.getUniqueId());
         return attackedAt != null && System.currentTimeMillis() - attackedAt < 30000L;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onGeneralDamage(EntityDamageEvent e) {
      if (e instanceof EntityDamageByEntityEvent edbe) {
         if (edbe.getDamager() instanceof AbstractWindCharge) {
            Player shooter = this.resolvePlayer(edbe.getDamager());
            if (shooter != null && (this.allowed(shooter, e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT) || this.allowed(shooter, e.getEntity().getLocation(), ClaimPermission.BUILD))) {
               return;
            }
         }
      }

      if ((e.getCause() == DamageCause.ENTITY_EXPLOSION || e.getCause() == DamageCause.BLOCK_EXPLOSION) && !this.allowsExplosions(e.getEntity().getLocation())) {
         e.setCancelled(true);
      }

   }

   private Player resolvePlayer(Entity damager) {
      if (damager instanceof Player p) {
         return p;
      } else {
         if (damager instanceof Projectile proj) {
            ProjectileSource var4 = proj.getShooter();
            if (var4 instanceof Player p2) {
               return p2;
            }
         }

         return null;
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onVehicleDamage(VehicleDamageEvent e) {
      Player player = this.resolvePlayer(e.getAttacker());
      if (player != null) {
         if (!this.allowed(player, e.getVehicle().getLocation(), ClaimPermission.BUILD) && !this.allowed(player, e.getVehicle().getLocation(), ClaimPermission.VEHICLES)) {
            e.setCancelled(true);
            this.deny(player);
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onVehicleDestroy(VehicleDestroyEvent e) {
      Player player = this.resolvePlayer(e.getAttacker());
      if (player != null) {
         if (!this.allowed(player, e.getVehicle().getLocation(), ClaimPermission.BUILD) && !this.allowed(player, e.getVehicle().getLocation(), ClaimPermission.VEHICLES)) {
            e.setCancelled(true);
            this.deny(player);
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onVehicleEnter(VehicleEnterEvent e) {
      Entity var3 = e.getEntered();
      if (var3 instanceof Player player) {
         if (!this.allowed(player, e.getVehicle().getLocation(), ClaimPermission.VEHICLES)) {
            e.setCancelled(true);
         }

      }
   }

   @EventHandler
   public void onVehicleMove(VehicleMoveEvent e) {
      Location from = e.getFrom();
      Location to = e.getTo();
      if (from.getBlockX() >> 4 != to.getBlockX() >> 4 || from.getBlockZ() >> 4 != to.getBlockZ() >> 4) {
         Vehicle vehicle = e.getVehicle();
         if (!vehicle.getPassengers().isEmpty()) {
            Player player = null;

            for(Entity passenger : vehicle.getPassengers()) {
               if (passenger instanceof Player) {
                  Player p = (Player)passenger;
                  player = p;
                  break;
               }
            }

            if (player != null) {
               Claim toClaim = this.claimManager.getClaimAt(to);
               if (toClaim != null) {
                  Ztone ztone = Ztone.getInstance();

                  for(Entity passenger : vehicle.getPassengers()) {
                     if (passenger instanceof Player) {
                        Player p = (Player)passenger;
                        if (!this.bypass.isActive(p) && (toClaim.isBanned(p.getUniqueId()) || ztone != null && ztone.isDragonEggBlockingClaim(p))) {
                           List<Entity> passengers = new ArrayList(vehicle.getPassengers());
                           vehicle.eject();
                           vehicle.teleport(from);

                           for(Entity pass : passengers) {
                              pass.teleport(from);
                              if (pass instanceof Player) {
                                 Player pl = (Player)pass;
                                 if (toClaim.isBanned(pl.getUniqueId())) {
                                    pl.sendMessage(Text.line(this.config.prefix() + this.config.message("cannot-enter-banned")));
                                 } else {
                                    pl.sendMessage(Text.line(this.config.prefix() + "<red>Du trägst das Drachenei und darfst keine Claims betreten!</red>"));
                                 }
                              }
                           }

                           return;
                        }
                     }
                  }

                  if (ztone != null && ztone.getPvpManager() != null) {
                     Pvpmanager pvpMgr = ztone.getPvpManager();
                     boolean passengerInCombat = false;
                     Iterator passengerIter = vehicle.getPassengers().iterator();

                     while(true) {
                        if (passengerIter.hasNext()) {
                           Entity passenger = (Entity)passengerIter.next();
                           if (!(passenger instanceof Player)) {
                              continue;
                           }

                           Player p = (Player)passenger;
                           if (!pvpMgr.getCombatManager().isTagged(p.getUniqueId()) || this.bypass.isActive(p) || pvpMgr.getOverrideManager().isOverriding(p.getUniqueId()) || this.pvpClaimBypass != null && this.pvpClaimBypass.isPlayerBypassed(p)) {
                              continue;
                           }

                           passengerInCombat = true;
                        }

                        if (passengerInCombat && (this.pvpClaimBypass == null || !this.pvpClaimBypass.isClaimBypassed(toClaim.id()))) {
                           List<Entity> passengers = new ArrayList(vehicle.getPassengers());
                           vehicle.eject();
                           vehicle.teleport(from);

                           for(Entity passenger : passengers) {
                              passenger.teleport(from);
                              if (passenger instanceof Player) {
                                 Player p = (Player)passenger;
                                 String msg = this.config.message("cannot-enter-combat");
                                 if (msg == null || msg.isBlank() || msg.equals("cannot-enter-combat")) {
                                    msg = "<red>ᴅᴜ ʙɪꜱᴛ ɪᴍ ᴋᴀᴍᴘꜰ! ᴅᴜ ᴋᴀɴɴꜱᴛ ᴋᴇɪɴᴇ ᴄʟᴀɪᴍꜱ ʙᴇᴛʀᴇᴛᴇɴ.</red>";
                                 }

                                 p.sendMessage(Text.line(this.config.prefix() + msg));
                              }
                           }

                           return;
                        }
                        break;
                     }
                  }

                  if (!this.allowed(player, to, ClaimPermission.VEHICLES)) {
                     List<Entity> passengers = new ArrayList(vehicle.getPassengers());
                     vehicle.eject();
                     vehicle.teleport(from);

                     for(Entity passenger : passengers) {
                        passenger.teleport(from);
                     }

                     player.sendMessage(Text.line(this.config.prefix() + this.config.message("action-denied-vehicle")));
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onWitherSpawn(CreatureSpawnEvent e) {
      if (e.getEntityType() == EntityType.WITHER) {
         if (e.getSpawnReason() == SpawnReason.BUILD_WITHER) {
            if (this.claimManager.getClaimAt(e.getLocation()) != null) {
               e.setCancelled(true);
            }

         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onExplosionPrime(ExplosionPrimeEvent e) {
      Location explosionLocation = e.getEntity().getLocation();
      Entity entity = e.getEntity();
      if (entity instanceof AbstractWindCharge windCharge) {
         Player shooter = this.resolvePlayer(windCharge);
         if (shooter != null) {
            if (!this.allowed(shooter, explosionLocation, ClaimPermission.ENTITY_INTERACT) && !this.allowed(shooter, explosionLocation, ClaimPermission.BUILD)) {
               e.setCancelled(true);
            }
         } else {
            Claim claim = this.claimManager.getClaimAt(explosionLocation);
            if (claim != null) {
               e.setCancelled(true);
            }
         }
      } else if (!this.allowsExplosions(explosionLocation)) {
         e.setCancelled(true);
         entity = e.getEntity();
         if (entity instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed)entity;
            tnt.remove();
         }

      } else {
         int radius = this.config.explosionClaimBorderRadius();

         for(Claim nearby : this.claimManager.getClaimsWithinRadius(explosionLocation, radius)) {
            boolean ownerAllowsExplosions = (Boolean)this.claimManager.findPlayerData(nearby.owner()).map(PlayerData::explosionsEnabled).orElse(false);
            if (!ownerAllowsExplosions) {
               e.setCancelled(true);
               Entity var8 = e.getEntity();
               if (var8 instanceof TNTPrimed) {
                  TNTPrimed tnt = (TNTPrimed)var8;
                  tnt.remove();
               }

               return;
            }
         }

      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityExplode(EntityExplodeEvent e) {
      Location explosionLocation = e.getLocation();
      Entity var4 = e.getEntity();
      if (var4 instanceof AbstractWindCharge windCharge) {
         Player shooter = this.resolvePlayer(windCharge);
         if (shooter != null) {
            if (!this.allowed(shooter, explosionLocation, ClaimPermission.ENTITY_INTERACT) && !this.allowed(shooter, explosionLocation, ClaimPermission.BUILD)) {
               e.setCancelled(true);
               e.blockList().clear();
            }
         } else {
            Claim claim = this.claimManager.getClaimAt(explosionLocation);
            if (claim != null) {
               e.setCancelled(true);
               e.blockList().clear();
            }
         }
      } else if (!this.allowsExplosions(explosionLocation)) {
         e.setCancelled(true);
         e.blockList().clear();
      } else {
         int radius = this.config.explosionClaimBorderRadius();

         for(Claim nearby : this.claimManager.getClaimsWithinRadius(explosionLocation, radius)) {
            boolean ownerAllowsExplosions = (Boolean)this.claimManager.findPlayerData(nearby.owner()).map(PlayerData::explosionsEnabled).orElse(false);
            if (!ownerAllowsExplosions) {
               e.setCancelled(true);
               e.blockList().clear();
               return;
            }
         }

         e.blockList().removeIf((block) -> !this.allowsExplosions(block.getLocation()));
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockExplode(BlockExplodeEvent e) {
      Location explosionLocation = e.getBlock().getLocation();
      if (!this.allowsExplosions(explosionLocation)) {
         e.setCancelled(true);
         e.blockList().clear();
      } else {
         int radius = this.config.explosionClaimBorderRadius();

         for(Claim nearby : this.claimManager.getClaimsWithinRadius(explosionLocation, radius)) {
            boolean ownerAllowsExplosions = (Boolean)this.claimManager.findPlayerData(nearby.owner()).map(PlayerData::explosionsEnabled).orElse(false);
            if (!ownerAllowsExplosions) {
               e.setCancelled(true);
               e.blockList().clear();
               return;
            }
         }

         e.blockList().removeIf((block) -> !this.allowsExplosions(block.getLocation()));
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onEntityInteract(PlayerInteractEntityEvent e) {
      Entity target = e.getRightClicked();
      Player player = e.getPlayer();
      if (!(target instanceof Player) || target.hasMetadata("NPC")) {
         if (!target.getScoreboardTags().contains("stonesit_seat") && !target.getScoreboardTags().contains("stonesit_playersit")) {
            if (target instanceof Vehicle) {
               if (!this.allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, target.getLocation(), ClaimPermission.VEHICLES)) {
                  e.setCancelled(true);
                  this.deny(player);
               }

            } else {
               if (!this.allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                  e.setCancelled(true);
                  this.deny(player);
               }

            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onEntityInteractAt(PlayerInteractAtEntityEvent e) {
      Entity target = e.getRightClicked();
      Player player = e.getPlayer();
      if (!(target instanceof Player) || target.hasMetadata("NPC")) {
         if (!target.getScoreboardTags().contains("stonesit_seat") && !target.getScoreboardTags().contains("stonesit_playersit")) {
            if (target instanceof Vehicle) {
               if (!this.allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT) && !this.allowed(player, target.getLocation(), ClaimPermission.VEHICLES)) {
                  e.setCancelled(true);
                  this.deny(player);
               }

            } else {
               if (!this.allowed(player, target.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
                  e.setCancelled(true);
                  this.deny(player);
               }

            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
      if (!this.allowed(e.getPlayer(), e.getRightClicked().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onShear(PlayerShearEntityEvent e) {
      if (!this.allowed(e.getPlayer(), e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onLeash(PlayerLeashEntityEvent e) {
      if (!this.allowed(e.getPlayer(), e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onUnleash(PlayerUnleashEntityEvent e) {
      if (!this.allowed(e.getPlayer(), e.getEntity().getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onFish(PlayerFishEvent e) {
      Entity caught = e.getCaught();
      if (caught != null && !this.allowed(e.getPlayer(), caught.getLocation(), ClaimPermission.ENTITY_INTERACT)) {
         e.setCancelled(true);
         this.deny(e.getPlayer());
      }

   }
}
