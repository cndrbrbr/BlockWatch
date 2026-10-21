package io.github.cndrbrbr.blockwatch.listener;

import io.github.cndrbrbr.blockwatch.command.BlockWatchCommand;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Inspektionsmodus (/bw inspect): Linksklick zeigt den Verlauf des
 * angeklickten Blocks, Rechtsklick den des Platzes vor der angeklickten
 * Seite - dort, wo z. B. ein abgebauter Block war. Der Klick selbst wird
 * abgebrochen, damit nichts abgebaut oder gesetzt wird.
 */
public final class InspectListener implements Listener {

    private final BlockWatchCommand command;

    public InspectListener(BlockWatchCommand command) {
        this.command = command;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || !command.isInspecting(event.getPlayer())) {
            return;
        }
        Block target;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            target = event.getClickedBlock();
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            target = event.getClickedBlock().getRelative(event.getBlockFace());
        } else {
            return;
        }
        event.setCancelled(true);
        command.showBlock(event.getPlayer(), target);
    }
}
