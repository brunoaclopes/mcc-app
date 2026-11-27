package mcc.app.ui

import mcc.recipe.Accessory

/** The icon for each original accessory. */
fun Accessory.icon(): Icon = when (this) {
    Accessory.MIXER -> Icon.WHISK
    Accessory.BASKET -> Icon.BASKET
    Accessory.STEAMER, Accessory.STEAMER_FLAT -> Icon.STEAMER
    Accessory.CUP -> Icon.BEAKER
}
