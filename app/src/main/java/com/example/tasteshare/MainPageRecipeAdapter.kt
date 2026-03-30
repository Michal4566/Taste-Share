package com.example.tasteshare

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MainPageRecipeAdapter(
    private val recipes: List<MainPageRecipe>,
    private val listener: OnRecipeClickListener? = null
) : RecyclerView.Adapter<MainPageRecipeAdapter.RecipeItemViewHolder>() {

    interface OnRecipeClickListener {
        fun onRecipeClicked(recipe: Recipe)
    }

    inner class RecipeItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recipeImage: ImageView = itemView.findViewById(R.id.recipe_image)
        val recipeName: TextView = itemView.findViewById(R.id.recipe_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.main_page_recipe_item, parent, false)
        return RecipeItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeItemViewHolder, position: Int) {
        val mainPageRecipe = recipes[position]
        val recipe = mainPageRecipe.recipe

        Glide.with(holder.itemView.context)
            .load(recipe.image)
            .placeholder(R.drawable.default_recipe_image)
            .into(holder.recipeImage)

        holder.recipeName.text = recipe.label

        holder.itemView.setOnClickListener {
            if (listener != null) {
                listener.onRecipeClicked(recipe)
            } else {
                val context = holder.itemView.context
                val intent = Intent(context, RecipeDetailActivity::class.java).apply {
                    putExtra("recipe", recipe)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = recipes.size
}
