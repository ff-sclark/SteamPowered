package com.teammoeg.steampowered.tileentity.burner;

import com.simibubi.create.content.contraptions.goggles.IHaveGoggleInformation;
import com.teammoeg.steampowered.block.burner.BurnerBlock;
import com.teammoeg.steampowered.block.engine.SteamEngineBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.List;

public abstract class BurnerTileEntity extends TileEntity implements ITickableTileEntity, IHaveGoggleInformation {
    private ItemStackHandler inv = new ItemStackHandler() {

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (ForgeHooks.getBurnTime(stack) != 0) return true;
            return false;
        }

    };
    int HURemain;
    private LazyOptional<IItemHandler> holder = LazyOptional.of(() -> inv);

    public BurnerTileEntity(TileEntityType<?> p_i48289_1_) {
        super(p_i48289_1_);
    }

    // Easy, easy
    public void readCustomNBT(CompoundNBT nbt) {
        inv.deserializeNBT(nbt.getCompound("inv"));
        HURemain = nbt.getInt("hu");
    }

    // Easy, easy
    public void writeCustomNBT(CompoundNBT nbt) {
        nbt.put("inv", inv.serializeNBT());
        nbt.putInt("hu", HURemain);
    }

    @Override
    public void load(BlockState state, CompoundNBT nbt) {
        super.load(state, nbt);
        readCustomNBT(nbt);
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        super.save(nbt);
        writeCustomNBT(nbt);
        return nbt;
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        CompoundNBT nbt = new CompoundNBT();
        this.writeCustomNBT(nbt);
        return new SUpdateTileEntityPacket(this.getBlockPos(), 3, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        this.readCustomNBT(pkt.getTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT nbt = super.getUpdateTag();
        writeCustomNBT(nbt);
        return nbt;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!this.holder.isPresent()) {
            this.refreshCapability();
        }
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? holder.cast() : super.getCapability(cap, side);
    }

    private void refreshCapability() {
        LazyOptional<IItemHandler> oldCap = this.holder;
        this.holder = LazyOptional.of(() -> {
            return this.inv;
        });
        oldCap.invalidate();
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            int emit = getHuPerTick();
            while (HURemain < emit && this.consumeFuel()) ;
            if (HURemain < emit) {
                if (HURemain > 0) {
                    emitHeat(HURemain);
                    HURemain = 0;
                }
                this.level.setBlockAndUpdate(this.worldPosition, state.setValue(BurnerBlock.LIT, false));
            } else {
                HURemain -= emit;
                emitHeat(emit);
                this.level.setBlockAndUpdate(this.worldPosition, state.setValue(BurnerBlock.LIT, true));
            }
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
            this.setChanged();
        }
    }

    private boolean consumeFuel() {
        int time = ForgeHooks.getBurnTime(inv.getStackInSlot(0), IRecipeType.SMELTING);
        if (time <= 0) return false;
        inv.getStackInSlot(0).shrink(1);
        HURemain += time * 24;//2.4HU/t

        return true;
    }

    protected void emitHeat(float value) {
        TileEntity receiver = level.getBlockEntity(this.getBlockPos().above());
        if (receiver instanceof IHeatReceiver) {
            ((IHeatReceiver) receiver).commitHeat(value);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<ITextComponent> tooltip, boolean isPlayerSneaking) {
        tooltip.add(componentSpacing.plainCopy().append(new TranslationTextComponent("tooltip.steampowered.burner.hu", HURemain).withStyle(TextFormatting.GOLD)));
        tooltip.add(componentSpacing.plainCopy().append(new TranslationTextComponent("tooltip.steampowered.burner.item", inv.getStackInSlot(0).getCount(), inv.getStackInSlot(0).getItem().getName(inv.getStackInSlot(0))).withStyle(TextFormatting.GRAY)));
        return true;
    }

    /*
     * HU per tick max, 10HU=1 steam
     * */
    protected abstract int getHuPerTick();
}
