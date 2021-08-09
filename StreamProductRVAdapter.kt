package com.mycompany.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mycompany.R
import com.mycompany.data.model.stream.Product
import com.mycompany.databinding.ItemStreamProductBinding
import kotlin.math.roundToInt

class StreamProductRVAdapter : ListAdapter<Product, StreamProductRVAdapter.StreamProductVH>(
    StreamProductDiffUtil()
) {

    private var selected_position = 0
    private var last_visible_item = 0

    var onItemSelected: ((Int)-> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): StreamProductVH {
        return StreamProductVH(
            ItemStreamProductBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(
        holder: StreamProductVH,
        @SuppressLint("RecyclerView") position: Int
    ) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener {
            if (selected_position != position) {
                selected_position = position
                notifyDataSetChanged()
            }
            onItemSelected?.let{
                it(position)
            }
        }
        holder.setItemIsSelected(selected_position == position)
        if(position == last_visible_item && selected_position != last_visible_item && position != itemCount-1){
            holder.setFadeItem()
        }

    }


    fun lastVisibleItemPosition(position: Int) {

        if(last_visible_item != position){
            notifyItemChanged(last_visible_item)
            last_visible_item = position
            notifyItemChanged(position)
        }


    }

    class StreamProductVH(binding: ItemStreamProductBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val mBinding = binding
        fun bind(model: Product) {
            Glide.with(itemView.context).load(model.previewImages.first().src).centerCrop()
                .into(mBinding.ivProduct)
            mBinding.tvCurrentPrice.text = String.format(
                itemView.context.resources.getString(R.string.format_current_price),
                model.discountPrice.roundToInt()
            )
            mBinding.tvPrice.text = String.format(
                itemView.context.resources.getString(R.string.format_not_price),
                model.price.roundToInt()
            )
            mBinding.tvName.text = model.name

        }

        fun setItemIsSelected(isSelected: Boolean) {
            if (isSelected) {
                mBinding.cardView.setCardBackgroundColor(ContextCompat.getColor(mBinding.root.context, R.color.white))
                mBinding.tvCurrentPrice.setTextColor(ContextCompat.getColor(mBinding.root.context, R.color.text_primary))
                mBinding.tvName.setTextColor(ContextCompat.getColor(mBinding.root.context, R.color.text_primary))
                mBinding.cardView.alpha = 1f
                removeAlpha()
            } else {
                mBinding.cardView.setCardBackgroundColor(ContextCompat.getColor(mBinding.root.context, R.color.black_50))
                mBinding.tvCurrentPrice.setTextColor(ContextCompat.getColor(mBinding.root.context, R.color.white))
                mBinding.tvName.setTextColor(ContextCompat.getColor(mBinding.root.context, R.color.white))
                mBinding.cardView.alpha = 1f
                removeAlpha()
            }
        }
        fun setFadeItem(){
            mBinding.cardView.setCardBackgroundColor(ContextCompat.getColor(mBinding.root.context, R.color.black_20))
            mBinding.cardView.alpha = 0.3f
            mBinding.tvName.alpha = 0.3f
            mBinding.tvPrice.alpha = 0.3f
            mBinding.tvCurrentPrice.alpha = 0.3f
            mBinding.clBg.alpha = 0.3f
        }

        fun removeAlpha(){
            mBinding.cardView.alpha = 1f
            mBinding.tvName.alpha = 1f
            mBinding.tvPrice.alpha = 1f
            mBinding.tvCurrentPrice.alpha = 1f
            mBinding.clBg.alpha = 1f
        }
    }

    class StreamProductDiffUtil : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id &&
                    oldItem.name == newItem.name &&
                    oldItem.price == newItem.price
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem == newItem
        }
    }
}