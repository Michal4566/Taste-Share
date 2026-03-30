package com.example.tasteshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CommentAdapter(
    private val comments: List<Comment>,
    private val currentUserId: String? = null, // Pass unique user ID instead of username
    private val onEditClick: ((Comment) -> Unit)? = null,
    private val onAvatarClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameTextView: TextView = itemView.findViewById(R.id.comment_user_name)
        private val commentTextView: TextView = itemView.findViewById(R.id.comment_text)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatar_image) // Correct ID for avatar
        private val editIcon: ImageView = itemView.findViewById(R.id.edit_user_comment_icon)

        fun bind(comment: Comment) {
            userNameTextView.text = comment.username
            commentTextView.text = comment.text

            // Load avatar or fallback to placeholder
            if (comment.imageUrl != null) {
                Glide.with(itemView.context)
                    .load(comment.imageUrl)
                    .placeholder(R.drawable.profile) // Placeholder if image is loading
                    .error(R.drawable.profile) // Fallback image in case of error
                    .into(avatarImageView)
            } else {
                avatarImageView.setImageResource(R.drawable.profile)
            }

            // Show edit icon only if the comment belongs to the current user
            if (currentUserId != null && comment.userId == currentUserId) {
                editIcon.visibility = View.VISIBLE
                editIcon.setOnClickListener {
                    onEditClick?.invoke(comment)
                }
            } else {
                editIcon.visibility = View.GONE
            }

            avatarImageView.setOnClickListener {
                onAvatarClick?.invoke(comment.userId)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comment_item, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size
}
