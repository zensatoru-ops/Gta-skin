package zen.gtasaskinstudio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import zen.gtasaskinstudio.app.gl.ModelGLView
import zen.gtasaskinstudio.app.i18n.Lang
import zen.gtasaskinstudio.app.i18n.StrKey
import zen.gtasaskinstudio.app.i18n.describe
import zen.gtasaskinstudio.app.i18n.fmt
import zen.gtasaskinstudio.app.i18n.str
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.rw.Severity
import zen.gtasaskinstudio.app.scene.FrameTree
import zen.gtasaskinstudio.app.scene.LoadedModel
import zen.gtasaskinstudio.app.scene.SceneBuffers
import zen.gtasaskinstudio.app.util.formatBytes
import zen.gtasaskinstudio.app.util.rwVersionString

/** Layar hasil: tab Model (3D), Info, Skeleton (hierarki + statistik skin), dan Catatan. */
@Composable
fun ModelViewerScreen(
    lang: Lang,
    loaded: LoadedModel,
    textures: List<TxdTexture>,
    txdLabel: String?,
    onPickTxd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val titles = listOf(StrKey.TabModel, StrKey.TabInfo, StrKey.TabSkeleton, StrKey.TabIssues)

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, key ->
                val suffix = if (i == 3 && loaded.issues.isNotEmpty()) " (${loaded.issues.size})" else ""
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(lang.str(key) + suffix) },
                )
            }
        }
        when (tab) {
            0 -> ModelTab(lang, loaded.scene, textures, txdLabel, onPickTxd, Modifier.fillMaxSize())
            1 -> InfoTab(lang, loaded)
            2 -> SkeletonTab(lang, loaded)
            else -> IssuesList(lang, loaded.issues, Modifier.fillMaxSize())
        }
    }
}

// ------------------------------------------------------------------ model

@Composable
private fun ModelTab(
    lang: Lang,
    scene: SceneBuffers?,
    textures: List<TxdTexture>,
    txdLabel: String?,
    onPickTxd: () -> Unit,
    modifier: Modifier,
) {
    var wire by rememberSaveable { mutableStateOf(false) }
    var colors by rememberSaveable { mutableStateOf(true) }
    var skeleton by rememberSaveable { mutableStateOf(false) }
    var texOn by rememberSaveable { mutableStateOf(true) }
    var resetTick by remember { mutableStateOf(0) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (scene == null) {
                Text(
                    text = lang.str(StrKey.CannotRender),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                GlHost(
                    scene = scene,
                    textures = textures,
                    wireframe = wire,
                    materialColors = colors,
                    showSkeleton = skeleton,
                    showTextures = texOn,
                    resetTick = resetTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (scene != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabeledCheck(lang.str(StrKey.Wireframe), wire) { wire = it }
                LabeledCheck(lang.str(StrKey.MaterialColors), colors) { colors = it }
                LabeledCheck(lang.str(StrKey.ShowSkeleton), skeleton) { skeleton = it }
                LabeledCheck(lang.str(StrKey.Textures), texOn) { texOn = it }
                OutlinedButton(onClick = onPickTxd) { Text(lang.str(StrKey.OpenTxd)) }
                OutlinedButton(onClick = { resetTick++ }) { Text(lang.str(StrKey.ResetCamera)) }
            }
            Text(
                text = lang.str(StrKey.ViewerHelp) + "\n" + (txdLabel ?: lang.str(StrKey.TxdNone)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LabeledCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(end = 4.dp))
    }
}

/** Menaruh GLSurfaceView ke Compose dan meneruskan onResume/onPause dari lifecycle. */
@Composable
private fun GlHost(
    scene: SceneBuffers,
    textures: List<TxdTexture>,
    wireframe: Boolean,
    materialColors: Boolean,
    showSkeleton: Boolean,
    showTextures: Boolean,
    resetTick: Int,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val view = remember { ModelGLView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }

    AndroidView(
        factory = { view },
        modifier = modifier,
        update = { v ->
            v.setScene(scene)
            v.setTextures(textures)
            v.setDisplay(wireframe, materialColors, showSkeleton, showTextures)
            if (v.lastResetTick != resetTick) {
                v.lastResetTick = resetTick
                v.resetCamera()
            }
        },
    )
}

// ------------------------------------------------------------------- info

@Composable
private fun InfoTab(lang: Lang, loaded: LoadedModel) {
    val m = loaded.model
    val g = m.geometries[loaded.geometryIndex]
    val textures = g.materials.mapNotNull { it.textureName }.filter { it.isNotEmpty() }.distinct()
    val yes = lang.str(StrKey.InfoYes)
    val no = lang.str(StrKey.InfoNo)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { InfoRow(lang.str(StrKey.FileName), loaded.displayName ?: "-") }
        item { InfoRow(lang.str(StrKey.FileSize), formatBytes(loaded.sizeBytes)) }
        item { InfoRow(lang.str(StrKey.InfoRw), rwVersionString(m.rwVersion)) }
        item { InfoRow(lang.str(StrKey.InfoGeometries), m.geometries.size.toString()) }
        item { InfoRow(lang.str(StrKey.InfoVertices), g.vertexCount.toString()) }
        item { InfoRow(lang.str(StrKey.InfoTriangles), g.triangleCount.toString()) }
        item { InfoRow(lang.str(StrKey.InfoMaterials), g.materials.size.toString()) }
        item { InfoRow(lang.str(StrKey.InfoUvLayers), g.uvLayerCount.toString()) }
        item { InfoRow(lang.str(StrKey.InfoPrelit), if (g.hasPrelit) yes else no) }
        item { InfoRow(lang.str(StrKey.InfoNormals), if (g.normals != null) yes else no) }
        item { InfoRow(lang.str(StrKey.InfoFrames), m.frames.size.toString()) }
        item { InfoRow(lang.str(StrKey.InfoSkinBones), g.skin?.numBones?.toString() ?: no) }
        item {
            InfoRow(
                lang.str(StrKey.InfoTextures),
                if (textures.isEmpty()) "-" else "${textures.size}: " + textures.joinToString(", "),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// --------------------------------------------------------------- skeleton

@Composable
private fun SkeletonTab(lang: Lang, loaded: LoadedModel) {
    val frames = loaded.model.frames
    val rows = remember(frames) { FrameTree.flatten(frames) }
    val stats = loaded.skinStats

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text(lang.str(StrKey.SkinTitle), style = MaterialTheme.typography.titleMedium) }
        if (stats == null) {
            item { Text(lang.str(StrKey.SkinNone)) }
        } else {
            item { InfoRow(lang.str(StrKey.InfoSkinBones), stats.numBones.toString()) }
            item { InfoRow(lang.str(StrKey.SkinMaxInfl), stats.maxInfluences.toString()) }
            item { InfoRow(lang.str(StrKey.SkinBadIndex), stats.badIndexVertices.toString()) }
            item { InfoRow(lang.str(StrKey.SkinBadSum), stats.badSumVertices.toString()) }
            item { InfoRow(lang.str(StrKey.SkinUnweighted), stats.unweightedVertices.toString()) }
            item {
                InfoRow(
                    lang.str(StrKey.SkinWeightRange),
                    String.format(java.util.Locale.US, "%.5f – %.5f", stats.minWeightSum, stats.maxWeightSum),
                )
            }
            item {
                val top = stats.verticesPerBone.withIndex()
                    .sortedByDescending { it.value }
                    .take(5)
                    .filter { it.value > 0 }
                    .joinToString(", ") { "#${it.index}: ${it.value}" }
                InfoRow(lang.str(StrKey.SkinTopBones), top.ifEmpty { "-" })
            }
        }
        item {
            Text(
                lang.str(StrKey.FrameTreeTitle) + " (${frames.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(rows) { row ->
            val f = frames[row.frameIndex]
            val name = f.name?.trim().takeUnless { it.isNullOrEmpty() } ?: lang.str(StrKey.FrameUnnamed)
            val bone = f.boneId?.let { "  · bone $it" } ?: ""
            Text(
                text = "${f.index}  $name$bone",
                modifier = Modifier.padding(start = (row.depth.coerceAtMost(20) * 12).dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ----------------------------------------------------------------- issues

@Composable
fun IssuesList(lang: Lang, issues: List<Issue>, modifier: Modifier = Modifier) {
    if (issues.isEmpty()) {
        Text(lang.str(StrKey.NoIssues), modifier = modifier.padding(16.dp))
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(issues) { issue ->
            Text(
                text = lang.describe(issue),
                color = if (issue.severity == Severity.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Dipakai MainActivity untuk pesan "berkas terlalu besar". */
fun tooLargeMessage(lang: Lang, maxMb: Int): String = lang.fmt(StrKey.FileTooLarge, maxMb)
