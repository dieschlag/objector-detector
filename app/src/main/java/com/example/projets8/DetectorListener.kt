package com.example.projets8

interface DetectorListener {
    fun onEmptyDetect()
    fun onDetect(boundingBoxes: List<Box>, inferenceTime: Long)
}