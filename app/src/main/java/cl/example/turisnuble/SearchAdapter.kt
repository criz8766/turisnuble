package cl.example.turisnuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchAdapter(
    private var results: List<SearchResult>,
    // Usamos una "lambda" para manejar el click en la Activity
    private val onItemClicked: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val result = results[position]
        holder.bind(result)
    }

    override fun getItemCount(): Int = results.size

    fun updateResults(newResults: List<SearchResult>) {
        results = newResults
        notifyDataSetChanged() // Simple, pero efectivo
    }

    inner class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.tv_result_title)
        private val subtitleView: TextView = itemView.findViewById(R.id.tv_result_subtitle)
        private val iconView: ImageView = itemView.findViewById(R.id.iv_result_icon)

        init {
            // Configuramos el click listener
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClicked(results[adapterPosition])
                }
            }
        }

        fun bind(result: SearchResult) {
            titleView.text = result.title
            subtitleView.text = result.subtitle

            // Asignamos un ícono simple basado en el tipo
            val iconRes = when (result.type) {
                SearchResultType.PARADERO -> R.drawable.ic_paradero // Usamos tu ícono existente
                SearchResultType.RUTA -> R.drawable.ic_bus       // Usamos tu ícono existente
                SearchResultType.TURISMO -> R.drawable.ic_turismo   // Usamos tu ícono existente
            }
            iconView.setImageResource(iconRes)
        }
    }
}