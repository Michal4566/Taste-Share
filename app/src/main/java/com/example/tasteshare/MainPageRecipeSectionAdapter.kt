package com.example.tasteshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainPageRecipeSectionAdapter(
    private val sections: List<MainPageRecipeSection>,
    private val listener: MainPageRecipeAdapter.OnRecipeClickListener? = null
) : RecyclerView.Adapter<MainPageRecipeSectionAdapter.RecipeSectionViewHolder>() {

    inner class RecipeSectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sectionTitle: TextView = itemView.findViewById(R.id.section_title)
        val recipesRecyclerView: RecyclerView = itemView.findViewById(R.id.recipes_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeSectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.main_page_recipe_section, parent, false)
        return RecipeSectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeSectionViewHolder, position: Int) {
        val section = sections[position]

        holder.sectionTitle.text = section.title

        val itemAdapter = MainPageRecipeAdapter(section.recipes, listener)
        holder.recipesRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.recipesRecyclerView.adapter = itemAdapter
    }

    fun getSections(): List<MainPageRecipeSection> {
        return sections
    }

    override fun getItemCount(): Int = sections.size
}
