package com.songhub.offline.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.songhub.offline.data.MidiFile
import com.songhub.offline.databinding.ItemSongBinding

class SongAdapter(
    private var songs: List<MidiFile>,
    private val onClick: (MidiFile) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.txtSongTitle.text = song.title
        holder.itemView.setOnClickListener { onClick(song) }
    }

    override fun getItemCount(): Int = songs

    fun updateList(newSongs: List<MidiFile>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}
