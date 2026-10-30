package com.songhub.offline.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.songhub.offline.R
import com.songhub.offline.data.Prefs
import com.songhub.offline.data.Song
import com.songhub.offline.databinding.ItemFolderBinding
import com.songhub.offline.databinding.ItemPlaylistBinding
import com.songhub.offline.databinding.ItemSongBinding
import java.io.File

/** Listahan ng kanta. */
class SongAdapter(
    private val prefs: Prefs,
    private val onClick: (List<Song>, Int) -> Unit,
    private val onMore: (Song, View) -> Unit,
    private val onFavoriteChanged: () -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    class VH(val b: ItemSongBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<Song> = emptyList()
    private var favs: Set<String> = emptySet()

    fun submit(list: List<Song>) {
        items = list
        favs = prefs.favorites
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        val ctx = holder.itemView.context
        holder.b.code.text = s.code ?: (position + 1).toString()
        holder.b.title.text = s.title
        holder.b.subtitle.text = s.folder
        val isFav = s.path in favs
        holder.b.fav.setImageResource(if (isFav) R.drawable.ic_star else R.drawable.ic_star_border)
        holder.b.fav.setColorFilter(ContextCompat.getColor(ctx, if (isFav) R.color.accent else R.color.text_secondary))
        holder.b.fav.setOnClickListener {
            prefs.toggleFavorite(s.path)
            favs = prefs.favorites
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) notifyItemChanged(p)
            onFavoriteChanged()
        }
        holder.b.root.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items, p)
        }
        holder.b.more.setOnClickListener { onMore(s, it) }
    }
}

/** Listahan ng playlists. Ang unang row ay "Bagong Playlist". */
class PlaylistAdapter(
    private val onOpen: (String) -> Unit,
    private val onCreate: () -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

    class VH(val b: ItemPlaylistBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<Pair<String, Int>> = emptyList()

    fun submit(list: List<Pair<String, Int>>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size + 1

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.itemView.context
        if (position == 0) {
            holder.b.icon.setImageResource(R.drawable.ic_add)
            holder.b.name.text = ctx.getString(R.string.new_playlist)
            holder.b.count.text = ""
            holder.b.root.setOnClickListener { onCreate() }
            return
        }
        val (name, count) = items[position - 1]
        holder.b.icon.setImageResource(R.drawable.ic_playlist)
        holder.b.name.text = name
        holder.b.count.text = ctx.getString(R.string.song_count, count)
        holder.b.root.setOnClickListener { onOpen(name) }
    }
}

/** Isang row sa built-in folder picker. */
data class FolderEntry(val file: File, val label: String, val subtitle: String, val kind: Int) {
    companion object {
        const val KIND_INTERNAL = 0
        const val KIND_SD = 1
        const val KIND_DIR = 2
        const val KIND_MIDI = 3
    }
}

class FolderAdapter(private val onOpen: (FolderEntry) -> Unit) : RecyclerView.Adapter<FolderAdapter.VH>() {

    class VH(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<FolderEntry> = emptyList()

    fun submit(list: List<FolderEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val ctx = holder.itemView.context
        holder.b.name.text = e.label
        holder.b.subtitle.text = e.subtitle
        holder.b.subtitle.visibility = if (e.subtitle.isEmpty()) View.GONE else View.VISIBLE
        val (icon, color) = when (e.kind) {
            FolderEntry.KIND_INTERNAL -> R.drawable.ic_smartphone to R.color.accent2
            FolderEntry.KIND_SD -> R.drawable.ic_sd_card to R.color.accent
            FolderEntry.KIND_DIR -> R.drawable.ic_folder to R.color.accent
            else -> R.drawable.ic_music_note to R.color.text_secondary
        }
        holder.b.icon.setImageResource(icon)
        holder.b.icon.setColorFilter(ContextCompat.getColor(ctx, color))
        holder.b.root.alpha = if (e.kind == FolderEntry.KIND_MIDI) 0.6f else 1f
        holder.b.root.setOnClickListener { onOpen(e) }
    }
}
