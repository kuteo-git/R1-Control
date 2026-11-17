package com.r1control.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaCamera
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.icons.MdiIcons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

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
        Text(
            camera.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.BottomStart).padding(Space.Medium),
        )
    }
}

/** The camera full screen: the stream filling the screen on black, a close button over it. Back closes it. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CameraFullscreen(camera: HaCamera, baseUrl: String, pathOf: suspend (HaEntityId) -> String, onClose: () -> Unit) {
    BackHandler(true) { onClose() }
    ApplyCameraFullscreen()
    val url = streamUrl(camera.entity, baseUrl, pathOf)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (url.isNotEmpty()) CameraStream(url, Modifier.fillMaxSize())
        Text(
            camera.name,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopStart).padding(Space.Large),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(Space.Large)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Tokens.Card)
                .tappable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            MdiIcons.byName("close")?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}
