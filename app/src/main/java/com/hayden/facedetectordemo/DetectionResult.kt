package com.hayden.facedetectordemo

data class DetectionResult(
    /**
     * 是否有人脸
     */
    var hasFace: Boolean = false,
    /**
     * 活体的可信度
     */
    var confidence: Float = 0.0.toFloat(),
)
