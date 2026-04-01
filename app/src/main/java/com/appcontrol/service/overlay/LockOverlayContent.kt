package com.appcontrol.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.appcontrol.R
import com.appcontrol.domain.model.LockReason

@Composable
internal fun LockOverlayContent(
    lockReason: LockReason,
    isForcedLock: Boolean,
    onGoHome: () -> Unit
) {
    // 强制锁定模式下使用不透明的全屏背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isForcedLock) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .zIndex(999f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = if (isForcedLock) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                when (lockReason) {
                    is LockReason.UsageLimitExceeded -> {
                        Text(
                            text = stringResource(R.string.lock_usage_limit_title),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = if (isForcedLock) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(
                                R.string.lock_usage_limit_detail,
                                formatDuration(lockReason.usedSeconds)
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = if (isForcedLock) {
                                Color.White.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    is LockReason.OutsideAllowedPeriod -> {
                        Text(
                            text = stringResource(R.string.lock_outside_period_title),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = if (isForcedLock) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val detail = if (lockReason.nextPeriodStart != null) {
                            stringResource(
                                R.string.lock_outside_period_detail,
                                lockReason.nextPeriodStart
                            )
                        } else {
                            stringResource(R.string.lock_outside_period_no_next)
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = if (isForcedLock) {
                                Color.White.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    is LockReason.NotLocked -> { /* Should not happen */ }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isForcedLock) {
                    // 强制锁定模式：显示警告信息，没有退出按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lock_forced_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "限制解除后将自动解锁",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // 普通模式：提供返回桌面按钮
                    Button(onClick = onGoHome) {
                        Text(text = stringResource(R.string.lock_go_home))
                    }
                }
            }
        }
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}小时${minutes}分钟"
    } else {
        "${minutes}分钟"
    }
}
