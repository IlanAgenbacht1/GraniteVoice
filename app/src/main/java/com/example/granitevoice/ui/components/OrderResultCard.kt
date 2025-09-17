package com.example.granitevoice.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.granitevoice.domain.model.OrderProgress
import com.example.granitevoice.domain.model.ProgressColor
import com.example.granitevoice.domain.usecase.OrderStatusInfo
import com.example.granitevoice.ui.theme.GraniteBlue
import com.example.granitevoice.ui.theme.GraniteError
import com.example.granitevoice.ui.theme.GraniteSuccess
import com.example.granitevoice.ui.theme.GraniteWarning

@Composable
fun OrderResultCard(
    orderStatusInfo: OrderStatusInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Remove transparency
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Order header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrderStatusHeader(orderStatusInfo = orderStatusInfo)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order details
            OrderDetailsSection(orderStatusInfo = orderStatusInfo)

            // Progress information if available
            orderStatusInfo.orderProgress?.let { progress ->
                Spacer(modifier = Modifier.height(16.dp))
                OrderProgressSection(orderProgress = progress)
            }
        }
    }
}

@Composable
private fun OrderStatusHeader(orderStatusInfo: OrderStatusInfo) {
    val (icon, color, statusText) = getOrderStatusIconAndColor(orderStatusInfo)

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Order ${orderStatusInfo.orderNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GraniteBlue
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
private fun OrderDetailsSection(orderStatusInfo: OrderStatusInfo) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Basic order information
        DetailRow(
            label = "Status",
            value = orderStatusInfo.statusDisplay
        )

        DetailRow(
            label = "Created",
            value = orderStatusInfo.createDate
        )

        DetailRow(
            label = "Last Updated",
            value = orderStatusInfo.auditDate
        )

        // Description if available
        orderStatusInfo.description?.let { desc ->
            DetailRow(
                label = "Details",
                value = desc,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun OrderProgressSection(orderProgress: OrderProgress) {
    Column {
        Text(
            text = "Progress Details",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = GraniteBlue,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Remove nested Card - use background instead
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Progress bar
                ProgressIndicatorSection(orderProgress = orderProgress)

                // Quantity information
                if (orderProgress.qty > 0 || orderProgress.actionQty > 0) {
                    QuantityProgressSection(orderProgress = orderProgress)
                }

                // Site information if available
                orderProgress.site?.let { site ->
                    DetailRow(
                        label = "Site",
                        value = site
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressIndicatorSection(orderProgress: OrderProgress) {
    val animatedProgress by animateFloatAsState(
        targetValue = orderProgress.percentComplete / 100f,
        animationSpec = tween(1000),
        label = "progress"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = orderProgress.getStatusDisplay(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = orderProgress.getProgressDisplay(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = getProgressColor(orderProgress.getProgressColor())
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Custom progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(getProgressColor(orderProgress.getProgressColor()))
            )
        }
    }
}

@Composable
private fun QuantityProgressSection(orderProgress: OrderProgress) {
    val actionQty = orderProgress.actionQty.toInt()
    val totalQty = orderProgress.qty.toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Quantity Progress",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = orderProgress.getQuantityDisplay(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }

    // Quantity progress bar if we have both values
    if (totalQty > 0 && actionQty >= 0) {
        val qtyProgress = if (totalQty > 0) actionQty.toFloat() / totalQty.toFloat() else 0f

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(qtyProgress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GraniteBlue)
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    maxLines: Int = 1
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            modifier = Modifier.weight(0.7f)
        )
    }
}

/**
 * Get icon and color for order status
 */
private fun getOrderStatusIconAndColor(orderStatusInfo: OrderStatusInfo): Triple<ImageVector, Color, String> {
    val orderProgress = orderStatusInfo.orderProgress

    return when {
        orderProgress?.isComplete() == true -> {
            Triple(
                Icons.Default.CheckCircle,
                Color(0xFF4CAF50),
                "Complete"
            ) // GraniteSuccess equivalent
        }

        orderProgress?.isBlocked() == true -> {
            Triple(Icons.Default.Error, Color(0xFFF44336), "Blocked") // GraniteError equivalent
        }

        orderProgress?.isInProgress() == true -> {
            Triple(
                Icons.Default.LocalShipping,
                Color(0xFFFF9800),
                "In Progress"
            ) // GraniteWarning equivalent
        }

        orderStatusInfo.isActive -> {
            Triple(Icons.Default.PlayArrow, Color(0xFF2196F3), "Active") // GraniteBlue equivalent
        }

        else -> {
            Triple(Icons.Default.Pause, Color(0xFF757575), "Inactive") // Gray
        }
    }
}

/**
 * Get progress color for UI
 */
private fun getProgressColorValue(progressColor: ProgressColor): Color {
    return when (progressColor) {
        ProgressColor.SUCCESS -> Color(0xFF4CAF50) // GraniteSuccess
        ProgressColor.ERROR -> Color(0xFFF44336) // GraniteError
        ProgressColor.WARNING -> Color(0xFFFF9800) // GraniteWarning
        ProgressColor.INFO -> Color(0xFF2196F3) // GraniteBlue
        ProgressColor.NEUTRAL -> Color(0xFF757575) // Gray
    }
}

@Composable
private fun getProgressColor(progressColor: ProgressColor): Color {
    return when (progressColor) {
        ProgressColor.SUCCESS -> GraniteSuccess
        ProgressColor.ERROR -> GraniteError
        ProgressColor.WARNING -> GraniteWarning
        ProgressColor.INFO -> GraniteBlue
        ProgressColor.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}