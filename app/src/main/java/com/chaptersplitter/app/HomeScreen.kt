package com.chaptersplitter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The menu shown when the app opens: pick the tool you want to use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    dark: Boolean,
    lastTool: String?,
    splitRunning: Boolean,
    splitProgress: Float,
    onOpen: (Tool) -> Unit,
    onToggleTheme: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val groups = Tool.entries.groupBy { it.group }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = cs.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("AuTools") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (dark) AppIcons.Sun else AppIcons.Moon,
                            contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.background
                ),
                scrollBehavior = scroll
            )
        }
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "intro") {
                Text(
                    "What do you want to do?",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
            groups.forEach { (group, tools) ->
                item(key = "g_$group") { SectionLabel(group) }
                items(tools, key = { it.id }) { tool ->
                    val running = tool == Tool.SPLIT && splitRunning
                    ToolCard(
                        tool = tool,
                        tag = when {
                            running -> "Running · ${(splitProgress * 100).toInt()}%"
                            tool.id == lastTool -> "Last used"
                            else -> null
                        },
                        progress = if (running) splitProgress else null,
                        onClick = { onOpen(tool) }
                    )
                }
            }
            item(key = "footer") {
                Text(
                    "AuTools ${BuildConfigInfo.VERSION}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(tool: Tool, tag: String?, progress: Float?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
                    if (tag != null) {
                        Surface(shape = RoundedCornerShape(8.dp), color = cs.secondaryContainer) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(tool.desc, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(50)),
                        trackColor = cs.primaryContainer
                    )
                }
            }
            Icon(AppIcons.Chevron, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}
