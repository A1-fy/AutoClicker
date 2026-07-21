package com.autoclicker.app.models

data class SwipeConfig(
    val pointAX: Float = 0f,
    val pointAY: Float = 0f,
    val pointBX: Float = 0f,
    val pointBY: Float = 0f,
    val frequency: Int = 10,
    val swipeDurationMs: Int = 10,
    val mode: OperationMode = OperationMode.SWIPE,
    val executionMode: ExecutionMode = ExecutionMode.ACCESSIBILITY
)

enum class OperationMode { TAP, SWIPE }
enum class ExecutionMode { ACCESSIBILITY, SHELL }
