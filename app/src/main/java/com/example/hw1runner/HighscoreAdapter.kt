package com.example.hw1runner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hw1runner.data.HighscoreEntry

class HighscoreAdapter(
    private val onItemClick: (HighscoreEntry, Int) -> Unit
) : RecyclerView.Adapter<HighscoreAdapter.ViewHolder>() {

    private var scores: List<HighscoreEntry> = emptyList()
    private var selectedPosition: Int = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankText: TextView = view.findViewById(R.id.rankText)
        val scoreText: TextView = view.findViewById(R.id.scoreText)
        val coinsText: TextView = view.findViewById(R.id.coinsText)
        val distanceText: TextView = view.findViewById(R.id.distanceText)
        val dateText: TextView = view.findViewById(R.id.dateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_highscore, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = scores[position]
        val rank = position + 1

        holder.rankText.text = rank.toString()
        holder.scoreText.text = entry.score.toString()
        holder.coinsText.text = entry.coins.toString()
        holder.distanceText.text = entry.getFormattedDistance()
        holder.dateText.text = entry.getFormattedDate()

        // Highlight rank colors
        val context = holder.itemView.context
        holder.rankText.setTextColor(
            when (rank) {
                1 -> context.getColor(R.color.score_gold)      // Gold for 1st
                2 -> context.getColor(R.color.neon_cyan)       // Silver-ish (cyan)
                3 -> context.getColor(R.color.neon_orange)     // Bronze-ish (orange)
                else -> context.getColor(R.color.white)
            }
        )

        // Highlight selected item
        holder.itemView.setBackgroundColor(
            if (position == selectedPosition) {
                0x33FFFFFF  // Semi-transparent white
            } else {
                0x00000000  // Transparent
            }
        )

        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.adapterPosition
            
            // Notify changes for animation
            if (previousSelected != -1) {
                notifyItemChanged(previousSelected)
            }
            notifyItemChanged(selectedPosition)
            
            onItemClick(entry, holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = scores.size

    fun submitList(newScores: List<HighscoreEntry>) {
        scores = newScores
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        val previousSelected = selectedPosition
        selectedPosition = position
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected)
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition)
        }
    }
}
