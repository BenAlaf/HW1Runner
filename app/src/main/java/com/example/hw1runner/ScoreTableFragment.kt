package com.example.hw1runner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hw1runner.data.AppDatabase
import com.example.hw1runner.data.HighscoreEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScoreTableFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var adapter: HighscoreAdapter

    private var onScoreSelectedListener: ((HighscoreEntry) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_score_table, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.scoresRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)

        adapter = HighscoreAdapter { entry, position ->
            onScoreSelectedListener?.invoke(entry)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadScores()
    }

    override fun onResume() {
        super.onResume()
        loadScores()
    }

    private fun loadScores() {
        viewLifecycleOwner.lifecycleScope.launch {
            val scores = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                db.highscoreDao().getTopTenScores()
            }

            if (scores.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                adapter.submitList(scores)
            }
        }
    }

    fun setOnScoreSelectedListener(listener: (HighscoreEntry) -> Unit) {
        onScoreSelectedListener = listener
    }

    companion object {
        fun newInstance(): ScoreTableFragment {
            return ScoreTableFragment()
        }
    }
}
