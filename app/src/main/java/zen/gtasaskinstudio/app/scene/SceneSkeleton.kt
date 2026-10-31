package zen.gtasaskinstudio.app.scene

/**
 * Salinan [SceneBuffers] dengan garis dan titik skeleton diganti; mesh, UV, dan draw tetap sama.
 * Copy of [SceneBuffers] with the skeleton lines and points replaced; mesh, UVs and draws stay the same.
 */
fun SceneBuffers.withSkeleton(
    lines: FloatArray,
    points: FloatArray,
    center: FloatArray = this.center,
    radius: Float = this.radius,
    vertices: FloatArray = this.vertices,
): SceneBuffers = SceneBuffers(
    vertices = vertices,
    vertexCount = vertexCount,
    indices = indices,
    draws = draws,
    wireIndices = wireIndices,
    skeletonLines = lines,
    center = center,
    radius = radius,
    materialNames = materialNames,
    framePositions = points,
    uvs = uvs,
)
