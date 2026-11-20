package com.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// The shape tiers (DESIGN-LANGUAGE.md; cards 16dp, buttons 12dp, sheets
// 20-24dp top corners, pills full, bottom-nav pill 28dp). Key ones:
val AnikutaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The bottom nav pill shape (28dp — matches Shapes.extraLarge). */
val BottomNavPillShape = RoundedCornerShape(28.dp)

/** The active-nav-pill shape (50% — fully rounded). */
val ActiveNavPillShape = RoundedCornerShape(50)
