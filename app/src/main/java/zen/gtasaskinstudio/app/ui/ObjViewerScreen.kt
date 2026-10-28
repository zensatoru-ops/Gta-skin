package zen.gtasaskinstudio.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zen.gtasaskinstudio.app.i18n.Lang
import zen.gtasaskinstudio.app.i18n.StrKey
import zen.gtasaskinstudio.app.i18n.fmt
import zen.gtasaskinstudio.app.i18n.str
import zen.gtasaskinstudio.app.rw.Issue
import zen.gtasaskinstudio.app.rw.TxdTexture
import zen.gtasaskinstudio.app.scene.SceneBuffers

/**
 * Viewer untuk model OBJ (Fase C). Memakai ulang tab Model DFF: rendering, checkbox,
 * dan tombol TXD. Tidak ada tab Info/Skeleton karena OBJ tidak punya skeleton.
 *
 * Viewer for OBJ models (Phase C). Reuses the DFF Model tab: rendering, checkboxes and
 * the TXD button. No Info/Skeleton tabs because OBJ has no skeleton.
 */
@Composable
fun ObjViewerScreen(
    lang: Lang,
    scene: SceneBuffers,
    issues: List<Issue>,
    vertexCount: Int,
    triangleCount: Int,
    mtlName: String?,
    showMtl: Boolean,
    onPickMtl: () -> Unit,
    textures: List<TxdTexture>,
    txdLabel: String?,
    onPickTxd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Ringkasan dan status MTL ditaruh di footer, dekat tombol. / Summary and MTL status go in the footer, next to the buttons.
        val summary = lang.fmt(StrKey.ObjSummary, vertexCount, triangleCount)
        val footer = if (showMtl) {
            summary + "\n" + (mtlName?.let { "${lang.str(StrKey.MtlLoaded)} $it" } ?: lang.str(StrKey.MtlNone))
        } else {
            summary
        }
        ModelTab(
            lang = lang,
            scene = scene,
            textures = textures,
            txdLabel = txdLabel,
            onPickTxd = onPickTxd,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onPickMtl = if (showMtl) onPickMtl else null,
            extraFooter = footer,
        )
        if (issues.isNotEmpty()) {
            IssuesList(
                lang = lang,
                issues = issues,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
            )
        }
    }
}
