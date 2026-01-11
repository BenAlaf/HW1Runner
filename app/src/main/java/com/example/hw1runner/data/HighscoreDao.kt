package com.example.hw1runner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HighscoreDao {
    
    @Query("SELECT * FROM highscores ORDER BY score DESC LIMIT 10")
    suspend fun getTopTenScores(): List<HighscoreEntry>
    
    @Query("SELECT * FROM highscores ORDER BY score DESC LIMIT 1")
    suspend fun getHighestScore(): HighscoreEntry?
    
    @Query("SELECT MAX(score) FROM highscores")
    suspend fun getHighScore(): Int?
    
    @Insert
    suspend fun insertScore(entry: HighscoreEntry)
    
    @Query("DELETE FROM highscores WHERE id NOT IN (SELECT id FROM highscores ORDER BY score DESC LIMIT 10)")
    suspend fun keepOnlyTopTen()
    
    @Query("SELECT COUNT(*) FROM highscores")
    suspend fun getCount(): Int
}
