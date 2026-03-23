package com.sayaem.nebula.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun SSection(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.textTertiary, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
fun STile(
    title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color, subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
            if (subtitle != null)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.textTertiary)
        }
        trailing?.invoke()
    }
}
