package com.example.tasteshare

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

class RecipeAdapter(
    private val context: Context,
    private val recipes: List<Recipe>,
    private val detailActivityClass: Class<*>
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recipe_item, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.bind(recipe)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, detailActivityClass)
            intent.putExtra("recipe", recipe)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = recipes.size

    inner class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.recipe_card)
        private val titleTextView: TextView = itemView.findViewById(R.id.recipe_title)
        private val cuisineTextView: TextView = itemView.findViewById(R.id.recipe_cuisine)
        private val timeTextView: TextView = itemView.findViewById(R.id.recipe_time)
        private val dishTypeTextView: TextView = itemView.findViewById(R.id.recipe_dish_type)
        private val mealTypeTextView: TextView = itemView.findViewById(R.id.additional_text_view)
        private val imageView: ImageView = itemView.findViewById(R.id.recipe_image)

        fun bind(recipe: Recipe) {
            titleTextView.text = recipe.label

            cuisineTextView.text = formatText(
                recipe.cuisineType.joinToString(", "),
                "Unknown cuisine"
            )
            timeTextView.text = if (recipe.totalTime == null || recipe.totalTime == 0.0) {
                "Unknown"
            } else {
                "${recipe.totalTime.roundToInt()} min"
            }
            dishTypeTextView.text = formatText(
                recipe.dishType.joinToString(", "),
                "Unknown Dish"
            )
            mealTypeTextView.text = formatText(
                recipe.mealType.joinToString(", "),
                "Unknown Meal"
            )

            Glide.with(context).load(recipe.image).into(imageView)
        }

        private fun formatText(text: String?, fallback: String, maxLength: Int = 15): String {
            return if (!text.isNullOrEmpty() && text.length <= maxLength) {
                text
            } else {
                fallback
            }
        }
    }
}
