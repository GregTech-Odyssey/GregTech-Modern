package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** An attached component extending a data item's native machine interaction. */
public interface IDataItemExtension extends IItemComponent {

    /**
     * Runs after the machine callback or settings copy/paste and receives its result. Ownership
     * failures and sneak-use of research items bypass extensions. Return the result to retain the
     * native outcome, or success when the extension handles an otherwise unhandled interaction.
     */
    InteractionResult afterDataItemUse(MetaMachine machine, ItemStack stack, UseOnContext context, InteractionResult result);
}
