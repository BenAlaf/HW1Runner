package com.example.hw1runner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highscores")
data class HighscoreEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val score: Int,
    val coins: Int,
    val distance: Float,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedDistance(): String {
        return if (distance >= 1000) {
            String.format("%.1fkm", distance / 1000f)
        } else {
            String.format("%.0fm", distance)
        }
    }
    
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
