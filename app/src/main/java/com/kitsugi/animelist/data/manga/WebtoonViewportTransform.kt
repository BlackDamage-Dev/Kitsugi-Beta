package com.kitsugi.animelist.data.manga

/** Geometry only; the UI applies this ONE transform to the entire strip viewport. */
object WebtoonViewportTransform {
    data class Result(val zoom: Float, val panX: Float, val scrollDelta: Float)

    fun calculate(
        oldZoom: Float, oldPanX: Float, factor: Float,
        centroidX: Float, centroidY: Float, panX: Float, panY: Float, viewportWidth: Int,
    ): Result {
        val old = MangaReaderPreferences.finite(oldZoom, 1f, 1f, 3f)
        if (!factor.isFinite() || factor <= 0f || !centroidX.isFinite() || !centroidY.isFinite() || !panX.isFinite() || !panY.isFinite()) {
            return Result(old, if (oldPanX.isFinite()) oldPanX else 0f, 0f)
        }
        val next = (old * factor).coerceIn(1f, 3f)
        val x = centroidX - viewportWidth.coerceAtLeast(0) / 2f
        val limit = viewportWidth.coerceAtLeast(0) * (next - 1f) / 2f
        val safePan = if (oldPanX.isFinite()) oldPanX else 0f
        return Result(
            next, ((safePan - x) * next / old + x + panX).coerceIn(-limit, limit),
            centroidY / old - centroidY / next - panY / next,
        )
    }
}
