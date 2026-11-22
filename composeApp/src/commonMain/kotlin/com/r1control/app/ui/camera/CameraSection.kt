package com.r1control.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaCamera
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.ui.components.DiscOverCover
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/** The red dot and LIVE wordmark on a dark pill, the one marker a live feed earns. */
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Row(
        modifier
            .clip(CircleShape)
            .background(DiscOverCover)
            .padding(horizontal = Space.Small, vertical = Space.Lines),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Lines),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
        Text(
            strings.live,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The full stream url for [entity], or empty until it is known: [baseUrl] is the server, [pathOf] asks for the HLS path. */
@Composable
private fun streamUrl(entity: HaEntityId, baseUrl: String, pathOf: suspend (HaEntityId) -> String): String {
    val path by produceState("", entity, baseUrl) { value = pathOf(entity) }
    return if (path.isEmpty()) "" else baseUrl + path
}

/** The household's cameras as cards at the top of the dashboard, each a live stream that opens full screen on a tap. */
fun LazyListScope.cameraItems(
    cameras: List<HaCamera>,
    baseUrl: String,
    pathOf: suspend (HaEntityId) -> String,
    onOpen: (HaCamera) -> Unit,
) {
    items(cameras, key = { "camera:${it.entity.value}" }) { camera ->
        CameraCard(camera, streamUrl(camera.entity, baseUrl, pathOf), onOpen = { onOpen(camera) })
    }
}

@Composable
private fun CameraCard(camera: HaCamera, url: String, onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Gutter, vertical = Space.Lines)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(Radius.Card))
            .background(Color.Black)
            .tappable(role = Role.Button, onClick = onOpen),
    ) {
        if (url.isNotEmpty()) CameraStream(url, Modifier.fillMaxSize())
        // A foot wash so the name reads over a bright frame.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))),
            ),
        )
        LiveBadge(Modifier.align(Alignment.TopStart).padding(Space.Medium))
        Text(
            camera.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(Space.Medium),
        )
    }
}

/**
 * The camera full screen: the stream filling the screen on black, under a controller in the Control app's
 * own idiom. A back disc and the name ride a top wash, a LIVE badge beside them. Back closes it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CameraFullscreen(camera: HaCamera, baseUrl: String, pathOf: suspend (HaEntityId) -> String, onClose: () -> Unit) {
    val strings = LocalStrings.current
    BackHandler(true) { onClose() }
    ApplyCameraFullscreen()
    val url = streamUrl(camera.entity, baseUrl, pathOf)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The whole picture in its own shape, and with sound; the cards are cropped and silent.
        if (url.isNotEmpty()) CameraStream(url, Modifier.fillMaxSize(), fill = false, muted = false)
        // A head wash so the chrome reads over the picture.
        Box(
            Modifier.fillMaxWidth().height(140.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
            ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = Space.Small, vertical = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            IconTap(R1Icons.Back, strings.back, onClose, tint = Color.White, background = DiscOverCover)
            Text(
                camera.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            LiveBadge()
            Spacer(Modifier.size(Space.Lines))
        }
    }
}
