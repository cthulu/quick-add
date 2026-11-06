package nl.freshlytyped.keepquickadd.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nl.freshlytyped.keepquickadd.R
import java.time.format.DateTimeFormatter

class EventPreviewAdapter :
    ListAdapter<CalendarEventItem, EventPreviewAdapter.EventViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_event_preview_row, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
    }

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val timeView: TextView = view.findViewById(R.id.tvEventTime)
        private val titleView: TextView = view.findViewById(R.id.tvEventTitle)
        val divider: View = view.findViewById(R.id.divider)

        fun bind(event: CalendarEventItem) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val start = event.startTime.format(formatter)
            val end = event.endTime.format(formatter)
            timeView.text = if (start == end) start else "$start – $end"
            titleView.text = event.title
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CalendarEventItem>() {
            // Keyed by event + start time: a recurring event can appear twice in a day.
            override fun areItemsTheSame(oldItem: CalendarEventItem, newItem: CalendarEventItem) =
                oldItem.id == newItem.id && oldItem.startTime == newItem.startTime

            override fun areContentsTheSame(oldItem: CalendarEventItem, newItem: CalendarEventItem) =
                oldItem == newItem
        }
    }
}
