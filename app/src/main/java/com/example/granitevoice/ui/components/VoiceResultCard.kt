package com.example.granitevoice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.granitevoice.domain.model.CommandIntent
import com.example.granitevoice.domain.model.ItemStockSummary
import com.example.granitevoice.domain.model.StockDetail
import com.example.granitevoice.domain.model.VoiceCommandResult
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.model.OrderProgress
import com.example.granitevoice.domain.model.ProgressColor
import com.example.granitevoice.domain.usecase.OrderStatusInfo
import com.example.granitevoice.ui.theme.GraniteAccent
import com.example.granitevoice.ui.theme.GraniteBlue
import com.example.granitevoice.ui.theme.GraniteBlueDark
import com.example.granitevoice.ui.theme.GraniteError
import com.example.granitevoice.ui.theme.GraniteSuccess
import com.example.granitevoice.ui.theme.GraniteWarning

@Composable
fun VoiceResultCard(
    result: VoiceCommandResult?,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSelectAlternative: ((MasterItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && result != null,
        enter = expandVertically(
            animationSpec = tween(300),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        result?.let { commandResult ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Header with status and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ResultStatusHeader(result = commandResult)

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main content based on result type
                    when (commandResult.intent) {
                        is CommandIntent.StockLookup -> {
                            StockLookupResultContent(
                                result = commandResult,
                                onSelectAlternative = onSelectAlternative
                            )
                        }

                        is CommandIntent.CheckOrderStatus -> {
                            OrderStatusResultContent(result = commandResult)
                        }

                        else -> {
                            GenericResultContent(result = commandResult)
                        }
                    }

                    // Confidence indicator if available
                    if (commandResult.confidence > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ConfidenceIndicator(confidence = commandResult.confidence)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStatusHeader(result: VoiceCommandResult) {
    val (icon, color, text) = when {
        // Order status results
        result.orderStatusInfo != null -> {
            val orderInfo = result.orderStatusInfo
            val (icon, color, statusText) = getOrderStatusIconAndColorDirect(orderInfo)
            Triple(icon, color, statusText)
        }

        // Stock lookup results  
        result.hasCompleteStockInfo() && result.stockSummary?.isInStock() == true -> {
            Triple(Icons.Default.CheckCircle, GraniteSuccess, "In Stock")
        }

        result.hasCompleteStockInfo() && result.stockSummary?.isInStock() == false -> {
            Triple(Icons.Default.Warning, GraniteError, "Out of Stock")
        }

        result.matchedItem != null && result.stockLookupError != null -> {
            Triple(Icons.Default.Warning, GraniteWarning, "Item Found")
        }

        result.matchedItem != null -> {
            Triple(Icons.Default.ShoppingCart, GraniteBlue, "Item Found")
        }

        else -> {
            Triple(Icons.Default.Warning, GraniteError, "No Match")
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun StockLookupResultContent(
    result: VoiceCommandResult,
    onSelectAlternative: ((MasterItem) -> Unit)?
) {
    Column {
        // Original query
        Text(
            text = "\"${result.originalTranscription}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main result
        if (result.matchedItem != null) {
            ItemDetailsCard(result = result)
        }

        // Alternative matches
        if (result.alternativeMatches.isNotEmpty() && onSelectAlternative != null) {
            Spacer(modifier = Modifier.height(16.dp))
            AlternativeMatchesSection(
                alternatives = result.alternativeMatches,
                onSelectAlternative = onSelectAlternative
            )
        }

        // Error message if any
        result.stockLookupError?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            ErrorMessageCard(message = error)
        }
    }
}

@Composable
private fun ItemDetailsCard(result: VoiceCommandResult) {
    val item = result.matchedItem!!
    val stockSummary = result.stockSummary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Item info
            Text(
                text = item.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GraniteBlue
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (stockSummary != null) {
                Spacer(modifier = Modifier.height(12.dp))

                // Stock quantity
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = if (stockSummary.isInStock()) GraniteSuccess else GraniteError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stockSummary.getTotalQuantityDisplay(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Location info with expand/collapse
                if (stockSummary.locationCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpandableLocationSection(stockSummary = stockSummary)
                }
            }
        }
    }
}

@Composable
private fun ExpandableLocationSection(stockSummary: ItemStockSummary) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        // Clickable summary row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stockSummary.getLocationSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse locations" else "Expand locations",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // Expandable location details
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(300),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(300),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(300))
        ) {
            LocationDetailsContent(stockDetails = stockSummary.stockDetails)
        }
    }
}

@Composable
private fun LocationDetailsContent(stockDetails: List<StockDetail>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Location Details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GraniteBlue
            )
            
            // Group locations with stock
            val locationsWithStock = stockDetails.filter { it.isInStock() }
            val locationsWithoutStock = stockDetails.filter { !it.isInStock() }
            
            if (locationsWithStock.isNotEmpty()) {
                LocationGroup(
                    title = "Available",
                    locations = locationsWithStock,
                    titleColor = GraniteSuccess
                )
            }
            
            if (locationsWithoutStock.isNotEmpty()) {
                LocationGroup(
                    title = "Out of Stock",
                    locations = locationsWithoutStock,
                    titleColor = GraniteError
                )
            }
        }
    }
}

@Composable
private fun LocationGroup(
    title: String,
    locations: List<StockDetail>,
    titleColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = titleColor
        )
        
        // Create elegant paragraph format
        val locationText = locations.joinToString(separator = " • ") { location ->
            val locationDisplay = location.getLocationDisplay()
            val quantityDisplay = location.getQuantityDisplay()
            
            if (location.isInStock()) {
                "$locationDisplay ($quantityDisplay)"
            } else {
                locationDisplay
            }
        }
        
        Text(
            text = locationText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun AlternativeMatchesSection(
    alternatives: List<MasterItem>,
    onSelectAlternative: (MasterItem) -> Unit
) {
    Column {
        Text(
            text = "Similar items found:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        alternatives.take(3).forEach { alternative ->
            Card(
                onClick = { onSelectAlternative(alternative) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alternative.code,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = GraniteBlue
                        )
                        Text(
                            text = alternative.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GraniteError.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = GraniteError,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = GraniteError
            )
        }
    }
}

@Composable
private fun ConfidenceIndicator(confidence: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(1000),
        label = "confidence"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Custom progress bar to avoid the dot issue
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            confidence >= 0.8f -> GraniteSuccess
                            confidence >= 0.6f -> GraniteWarning
                            else -> GraniteError
                        }
                    )
            )
        }
    }
}

@Composable
private fun GenericResultContent(result: VoiceCommandResult) {
    Column {
        Text(
            text = "\"${result.originalTranscription}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = result.getStatusMessage(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OrderStatusResultContent(result: VoiceCommandResult) {
    Column {
        Text(
            text = "\"${result.originalTranscription}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Show detailed order information if available
        if (result.orderStatusInfo != null) {
            OrderStatusDirectContent(orderStatusInfo = result.orderStatusInfo)
        } else {
            // Fallback to generic status message
            Text(
                text = result.getStatusMessage(),
                style = MaterialTheme.typography.bodyLarge
            )

            // Show errors if any
            if (result.stockLookupErrors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                result.stockLookupErrors.forEach { (key, error) ->
                    ErrorMessageCard(message = error)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderStatusDirectContent(orderStatusInfo: OrderStatusInfo) {
    // Add background container like stock lookup ItemDetailsCard
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Order header
            OrderStatusHeaderDirect(orderStatusInfo = orderStatusInfo)

            Spacer(modifier = Modifier.height(12.dp))

            // Order details
            OrderDetailsSectionDirect(orderStatusInfo = orderStatusInfo)

            // Progress information if available
            orderStatusInfo.orderProgress?.let { progress ->
                Spacer(modifier = Modifier.height(16.dp))
                OrderProgressSectionDirect(orderProgress = progress)
            }
        }
    }
}

@Composable
private fun OrderStatusHeaderDirect(orderStatusInfo: OrderStatusInfo) {
    // Get the color logic but don't use the icon
    val statusColor = getOrderStatusColor(orderStatusInfo)

    Column {
        Text(
            text = orderStatusInfo.orderNumber, // Remove "Order" prefix
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GraniteBlue
        )
        // Status directly under order number with elegant coloring
        Text(
            text = orderStatusInfo.statusDisplay,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = statusColor
        )
    }
}

/**
 * Get appropriate color for order status text
 */
@Composable
private fun getOrderStatusColor(orderStatusInfo: OrderStatusInfo): Color {
    val order = orderStatusInfo
    val orderProgress = orderStatusInfo.orderProgress

    return when {
        // ONLY show success color for actual COMPLETE status
        order.status.uppercase() == "COMPLETE" -> {
            GraniteSuccess
        }
        // Blocked statuses
        order.status.uppercase() == "CANCELLED" -> {
            GraniteError
        }

        order.status.uppercase() == "ONHOLD" -> {
            GraniteWarning
        }
        // In-progress statuses
        orderProgress?.isInProgress() == true -> {
            GraniteWarning
        }
        // Default for all other statuses (ENTERED, etc.)
        else -> {
            Color(0xFF2196F3)  // Same blue as "Order Found" in header
        }
    }
}

@Composable
private fun OrderDetailsSectionDirect(orderStatusInfo: OrderStatusInfo) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add customer/trading partner information
        orderStatusInfo.customerName?.let { customerName ->
            DetailRowDirect(
                label = "Customer",
                value = customerName
            )
        }

        DetailRowDirect(
            label = "Created",
            value = orderStatusInfo.getCreateDateWithTime()
        )

        DetailRowDirect(
            label = "Last Updated",
            value = orderStatusInfo.getAuditDateWithTime()
        )

        // Description if available
        orderStatusInfo.description?.let { desc ->
            DetailRowDirect(
                label = "Details",
                value = desc,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun OrderProgressSectionDirect(orderProgress: OrderProgress) {
    // Use Box with background instead of Card to avoid elevation artifacts
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress bar
            ProgressIndicatorSectionDirect(orderProgress = orderProgress)

            // Quantity information (text only, no duplicate progress bar)
            if (orderProgress.qty > 0 || orderProgress.actionQty > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quantity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = orderProgress.getQuantityDisplay(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Site information if available
            orderProgress.site?.let { site ->
                DetailRowDirect(
                    label = "Site",
                    value = site
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicatorSectionDirect(orderProgress: OrderProgress) {
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
                color = getProgressColorDirect(orderProgress.getProgressColor())
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
                    .background(getProgressColorDirect(orderProgress.getProgressColor()))
            )
        }
    }
}


@Composable
private fun DetailRowDirect(
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
 * Get icon and color for order status (direct version for inline use)
 */
private fun getOrderStatusIconAndColorDirect(orderStatusInfo: OrderStatusInfo): Triple<ImageVector, Color, String> {
    val order = orderStatusInfo
    val orderProgress = orderStatusInfo.orderProgress

    return when {
        // ONLY show "Order Complete" for actual COMPLETE status
        order.status.uppercase() == "COMPLETE" -> {
            Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "Order Complete")
        }
        // Blocked statuses
        order.status.uppercase() == "CANCELLED" -> {
            Triple(Icons.Default.Warning, Color(0xFFF44336), "Order Cancelled")
        }
        order.status.uppercase() == "ONHOLD" -> {
            Triple(Icons.Default.Warning, Color(0xFFFF9800), "Order On Hold")
        }
        // In-progress statuses
        orderProgress?.isInProgress() == true -> {
            Triple(Icons.Default.Info, Color(0xFFFF9800), "Order In Progress")
        }
        // Default for all other statuses (ENTERED, etc.)
        else -> {
            Triple(Icons.Default.Info, Color(0xFF2196F3), "Order Found")
        }
    }
}

/**
 * Get progress color for UI (direct version for inline use)
 */
@Composable
private fun getProgressColorDirect(progressColor: ProgressColor): Color {
    return when (progressColor) {
        ProgressColor.SUCCESS -> GraniteSuccess
        ProgressColor.ERROR -> GraniteError
        ProgressColor.WARNING -> GraniteWarning
        ProgressColor.INFO -> GraniteBlue
        ProgressColor.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}