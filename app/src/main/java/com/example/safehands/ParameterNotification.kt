package com.example.safehands

data class ParameterNotification(
    val id: String,
    val title: String,
    val body: String,
    val parameter: String,
    val time: String,
    val details: String,
    val timestamp: String
)
