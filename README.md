
Repository Information
=======

Repository for the guardians mod, Neoforge 1.21.1.

# NewGuardians

Mod para NeoForge 1.21.1 que adiciona a entidade **Guard**: um mob amigável, controlável e persistente, com progressão, equipamento e sistema de captura via "soul cage".

## Funcionalidades

- **3 modos de comportamento**: Following, Staying, Wandering (ciclo com shift+click)
- **Sistema de dono**: só o dono pode equipar, capturar ou mudar o modo do guard
- **Combate adaptativo**: melee, arco ou crossbow conforme a arma equipada; assist automático quando o dono ataca algo; guards do mesmo dono nunca se atacam
- **Aggro de mobs hostis**: guards atraem monstros próximos, com partilha de carga entre vários guards/jogadores
- **Progressão**: níveis 0-20 por XP (matar monstros), sobe vida máxima
- **Upgrades permanentes**: 3 níveis (mais vida, resistência, regen), comprados com itens via GUI própria
- **Guard Cage**: item de duas fases que captura/reinvoca o guard com todo o estado (equipamento, nível, modo); "soul link" devolve a alma à cage automaticamente se o guard morrer no mundo

## Estrutura

```
entity/    Guard, GuardMode, GuardProgressionData
item/      GuardCageItem, GuardSpawnItem
menu/      GuardEquipmentMenu, GuardEquipmentContainer
registry/  ModItems, ModEntities, ModAttachments, ModMenus, ModCreativeTabs
event/     GuardCombatHandler
client/    NewGuardiansModClient, GuardRenderer, GuardEquipmentScreen, GuardCageCooldownOverlay
```

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  

