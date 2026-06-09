$ErrorActionPreference = 'Stop'

$path = Join-Path $PSScriptRoot '../../main/java/com/gregtechceu/gtceu/api/transfer/item/CustomItemStackHandler.java'
$source = Get-Content -Raw -Path $path

$deserializePattern = 'public void deserializeNBT\(CompoundTag nbt\) \{\s*setSize\(Math\.max\(size, nbt\.getInt\("Size"\)\)\);\s*Arrays\.fill\(stacks, ItemStack\.EMPTY\);\s*ListTag tagList'
if ($source -notmatch $deserializePattern) {
    throw 'CustomItemStackHandler.deserializeNBT must clear existing slots before applying sparse non-empty NBT entries.'
}

$readBufPattern = 'public void readBuf\(LogicalSide side, @NotNull FriendlyByteBuf data\) \{\s*setSize\(Math\.max\(size, data\.readVarInt\(\)\)\);\s*Arrays\.fill\(stacks, ItemStack\.EMPTY\);\s*while \(data\.getByte\(data\.readerIndex\(\)\) != -1\)'
if ($source -notmatch $readBufPattern) {
    throw 'CustomItemStackHandler.readBuf must clear existing slots before applying sparse non-empty buffer entries.'
}

Write-Host 'CustomItemStackHandler missing slot clear regression check passed.'
