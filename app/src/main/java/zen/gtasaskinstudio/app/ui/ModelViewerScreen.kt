package zen.gtasaskinstudio.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zen.gtasaskinstudio.app.saf.SUPPORTED_MIME_TYPES
import zen.gtasaskinstudio.app.saf.loadSlotTexture
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
import zen.gtasaskinstudio.app.rig.AutoRig
import zen.gtasaskinstudio.app.rig.AutoRigResult
import zen.gtasaskinstudio.app.scene.SceneBuffers
import zen.gtasaskinstudio.app.scene.withSkeleton
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
internal fun ModelTab(
    lang: Lang,
    scene: SceneBuffers?,
    textures: List<TxdTexture>,
    txdLabel: String?,
    onPickTxd: () -> Unit,
    modifier: Modifier,
    /** Bila tidak null, tombol "Buka MTL" tampil di baris tombol. / When non-null, an "Open MTL" button is shown. */
    onPickMtl: (() -> Unit)? = null,
    /** Teks tambahan di bawah viewer (mis. status MTL). / Extra footer text (e.g. MTL status). */
    extraFooter: String? = null,
    /** Nilai awal "Balik UV": true untuk FBX/OBJ. / Initial "Flip UV" value: true for FBX/OBJ. */
    flipDefault: Boolean = false,
) {
    var wire by rememberSaveable { mutableStateOf(false) }
    var colors by rememberSaveable { mutableStateOf(true) }
    var skeleton by rememberSaveable { mutableStateOf(false) }
    var texOn by rememberSaveable { mutableStateOf(true) }
    var flipUv by rememberSaveable(scene, flipDefault) { mutableStateOf(flipDefault) }
    var resetTick by remember { mutableIntStateOf(0) }
    var showRig by remember { mutableStateOf(false) }
    var rigResult by remember(scene) { mutableStateOf<AutoRigResult?>(null) }
    // Tes rig: pose dari ped.ifp digambar di samping model. / Rig test: the ped.ifp pose is drawn beside the model.
    var rigTest by remember(scene) { mutableStateOf(false) }
    var rigMirror by remember(scene) { mutableStateOf(false) }
    var poseView by remember(scene) { mutableStateOf<PoseView?>(null) }
    // Rig estimasi digambar sebagai skeleton di atas model. Hanya untuk model tanpa skeleton asli.
    // The estimated rig is drawn as a skeleton over the model. Only for models without a real skeleton.
    val displayScene = remember(scene, rigResult, poseView) {
        val r = rigResult
        if (scene != null && r != null && scene.skeletonLines.isEmpty()) {
            val g = AutoRig.geometry(r)
            val pv = poseView
            if (pv == null) {
                scene.withSkeleton(g.lines, g.points)
            } else if (pv.shift[0] == 0f && pv.shift[1] == 0f && pv.shift[2] == 0f) {
                // Pose di tempat: mesh (bila dideformasi) dan skeleton berpose saja. / In-place pose: the (deformed) mesh and the posed skeleton only.
                scene.withSkeleton(pv.geometry.lines, pv.geometry.points, vertices = pv.vertices ?: scene.vertices)
            } else {
                val c = FloatArray(3) { scene.center[it] + pv.shift[it] * 0.5f }
                val shiftLen = Math.sqrt((pv.shift[0] * pv.shift[0] + pv.shift[1] * pv.shift[1] + pv.shift[2] * pv.shift[2]).toDouble()).toFloat()
                scene.withSkeleton(g.lines + pv.geometry.lines, g.points + pv.geometry.points, c, scene.radius + shiftLen * 0.5f)
            }
        } else {
            scene
        }
    }

    // Slot material: ketuk bagian model untuk memilih material, lalu pasang PNG/TGA.
    // Material slots: tap a part of the model to pick its material, then attach a PNG/TGA.
    var pickedSlot by remember(scene) { mutableStateOf<Int?>(null) }
    var slotTextures by remember(scene) { mutableStateOf<Map<Int, TxdTexture>>(emptyMap()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slotPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = pickedSlot
        pickedSlot = null
        if (slot == null || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tex = withContext(Dispatchers.Default) { loadSlotTexture(context, uri) }
            if (tex != null) slotTextures = slotTextures + (slot to tex)
        }
    }
    val slotIndex = pickedSlot
    if (slotIndex != null && scene != null) {
        val slotName = scene.materialNames.getOrNull(slotIndex) ?: "#$slotIndex"
        val current = slotTextures[slotIndex]
        AlertDialog(
            onDismissRequest = { pickedSlot = null },
            title = { Text(lang.fmt(StrKey.SlotTitle, slotName)) },
            text = {
                Text(if (current != null) "${lang.str(StrKey.SlotAttached)} ${current.name}" else lang.str(StrKey.SlotNone))
            },
            confirmButton = {
                TextButton(onClick = { slotPicker.launch(SUPPORTED_MIME_TYPES) }) {
                    Text(lang.str(StrKey.SlotAssign))
                }
            },
            dismissButton = {
                Row {
                    if (current != null) {
                        TextButton(onClick = {
                            slotTextures = slotTextures - slotIndex
                            pickedSlot = null
                        }) { Text(lang.str(StrKey.SlotClear)) }
                    }
                    TextButton(onClick = { pickedSlot = null }) { Text(lang.str(StrKey.SlotCancel)) }
                }
            },
        )
    }

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
                    scene = displayScene ?: scene,
                    textures = textures,
                    wireframe = wire,
                    materialColors = colors,
                    showSkeleton = skeleton,
                    showTextures = texOn,
                    resetTick = resetTick,
                    slotTextures = slotTextures,
                    onMaterialPick = { pickedSlot = it },
                    flipV = flipUv,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (scene != null) {
            // Panel bawah: opsi tampilan dalam dua kolom, status, lalu semua tombol aksi di paling bawah.
            // Bottom panel: display options in two columns, status, then all action buttons at the very bottom.
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!rigTest) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledCheck(lang.str(StrKey.Textures), texOn, Modifier.weight(1f)) { texOn = it }
                        LabeledCheck(lang.str(StrKey.Wireframe), wire, Modifier.weight(1f)) { wire = it }
                    }
                    if (!rigTest) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledCheck(lang.str(StrKey.MaterialColors), colors, Modifier.weight(1f)) { colors = it }
                        LabeledCheck(lang.str(StrKey.ShowSkeleton), skeleton, Modifier.weight(1f)) { skeleton = it }
                    }
                    if (!rigTest) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledCheck(lang.str(StrKey.FlipUv), flipUv, Modifier.weight(1f)) { flipUv = it }
                        Spacer(Modifier.weight(1f))
                    }
                    if (!rigTest) Text(
                        text = lang.str(StrKey.ViewerHelp) + "\n" + (txdLabel ?: lang.str(StrKey.TxdNone)) +
                            (extraFooter?.let { "\n" + it } ?: "") +
                            (if (slotTextures.isNotEmpty()) "\n" + lang.fmt(StrKey.SlotCount, slotTextures.size) else "") +
                            (rigResult?.let { "\n" + lang.fmt(StrKey.AutoRigUp, it.up.name) + ". " + lang.str(StrKey.AutoRigNote) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    if (!rigTest) Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionButton(lang.str(StrKey.OpenTxd), onPickTxd, Modifier.weight(1f))
                        if (onPickMtl != null) {
                            ActionButton(lang.str(StrKey.OpenMtl), onPickMtl, Modifier.weight(1f))
                        }
                        ActionButton(lang.str(StrKey.ResetCamera), { resetTick++ }, Modifier.weight(1f))
                    }
                    if (scene.skeletonLines.isEmpty() && !rigTest) {
                        ActionButton(
                            lang.str(StrKey.AutoRig),
                            {
                                scope.launch {
                                    val r = withContext(Dispatchers.Default) { AutoRig.fromInterleaved(scene.vertices, mirror = rigMirror) }
                                    rigResult = r
                                    if (r != null) skeleton = true else showRig = true
                                }
                            },
                            Modifier.fillMaxWidth(),
                        )
                    }
                    val rigNow = rigResult
                    if (scene.skeletonLines.isEmpty() && rigNow != null) {
                        if (!rigTest) {
                            ActionButton(
                                lang.str(StrKey.RigTest),
                                {
                                    skeleton = true
                                    rigTest = true
                                },
                                Modifier.fillMaxWidth(),
                            )
                        } else {
                            RigTestPanel(
                                lang = lang,
                                rig = rigNow,
                                scene = scene,
                                mirror = rigMirror,
                                onMirror = { m ->
                                    rigMirror = m
                                    scope.launch {
                                        val r = withContext(Dispatchers.Default) { AutoRig.fromInterleaved(scene.vertices, mirror = m) }
                                        if (r != null) rigResult = r
                                    }
                                },
                                onPose = { poseView = it },
                                onClose = { rigTest = false },
                            )
                        }
                    }
    if (showRig) {
        AlertDialog(
            onDismissRequest = { showRig = false },
            title = { Text(lang.str(StrKey.AutoRigTitle)) },
            text = {
                val r = rigResult
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(lang.str(StrKey.AutoRigNote), style = MaterialTheme.typography.bodySmall)
                    if (r == null) {
                        Text(lang.str(StrKey.AutoRigNone))
                    } else {
                        Text(lang.fmt(StrKey.AutoRigUp, r.up.name), modifier = Modifier.padding(vertical = 4.dp))
                        r.joints.forEach { j ->
                            Text(
                                String.format(java.util.Locale.US, "%-18s %+.3f %+.3f %+.3f", j.boneName, j.x, j.y, j.z),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRig = false }) { Text(lang.str(StrKey.SlotCancel)) } },
        )
    }
                }
            }
        }
    }
}

@Composable
private fun LabeledCheck(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier = modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(end = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tombol aksi di panel bawah; teks dipotong dengan elipsis bila sempit. / Bottom action button; text ellipsizes when narrow. */
@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    slotTextures: Map<Int, TxdTexture>,
    onMaterialPick: (Int) -> Unit,
    flipV: Boolean,
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
            v.setSlotTextures(slotTextures)
            v.onMaterialPick = onMaterialPick
            v.setFlipV(flipV)
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
