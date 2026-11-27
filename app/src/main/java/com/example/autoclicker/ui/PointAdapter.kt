package com.example.autoclicker.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.example.autoclicker.databinding.ItemPointBinding
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.PointType
import java.util.Collections

class PointAdapter(
    private val onChanged: () -> Unit,
    private val onPick: (ClickPoint) -> Unit
) : RecyclerView.Adapter<PointAdapter.VH>() {

    /** 展示用列表 */
    val items = mutableListOf<ClickPoint>()
    /** 配置里的真实列表（同一批对象引用），增删改要同步到它 */
    private var source: MutableList<ClickPoint>? = null

    inner class VH(val binding: ItemPointBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPointBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val point = items[position]
        with(holder.binding) {
            tvIndex.text = "点位 ${position + 1}"

            // 避免文本监听器互相触发导致的光标跳动
            spinnerType.setSelection(if (point.type == PointType.CLICK) 0 else 1)
            setText(etX, point.x.toString()) { point.x = it.toIntOrNull() ?: 0 }
            setText(etY, point.y.toString()) { point.y = it.toIntOrNull() ?: 0 }
            setText(etEndX, point.endX.toString()) { point.endX = it.toIntOrNull() ?: 0 }
            setText(etEndY, point.endY.toString()) { point.endY = it.toIntOrNull() ?: 0 }
            setText(etRepeat, point.repeatCount.toString()) { point.repeatCount = it.toIntOrNull() ?: 1 }
            setText(etInterval, point.intervalMs.toString()) { point.intervalMs = it.toLongOrNull() ?: 200L }
            setText(etDuration, point.pressDurationMs.toString()) { point.pressDurationMs = it.toLongOrNull() ?: 60L }
            setText(etJitter, point.jitterRadius.toString()) { point.jitterRadius = it.toIntOrNull() ?: 0 }

            spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    point.type = if (pos == 0) PointType.CLICK else PointType.SWIPE
                    onChanged()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }

            btnPick.setOnClickListener { onPick(point) }

            btnUp.setOnClickListener {
                if (position > 0) {
                    Collections.swap(items, position, position - 1)
                    source?.let { Collections.swap(it, position, position - 1) }
                    notifyItemMoved(position, position - 1)
                    notifyItemRangeChanged(position - 1, 2)
                    onChanged()
                }
            }
            btnDown.setOnClickListener {
                if (position < items.size - 1) {
                    Collections.swap(items, position, position + 1)
                    source?.let { Collections.swap(it, position, position + 1) }
                    notifyItemMoved(position, position + 1)
                    notifyItemRangeChanged(position, 2)
                    onChanged()
                }
            }
            btnDelete.setOnClickListener {
                items.removeAt(position)
                source?.let { if (position < it.size) it.removeAt(position) }
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, items.size - position)
                onChanged()
            }
        }
    }

    override fun getItemCount() = items.size

    /** 用配置列表刷新。若对象引用完全一致（只是字段被改），不做任何事以避免输入框失焦 */
    fun submit(list: MutableList<ClickPoint>) {
        source = list
        val same = items.size == list.size && items.zip(list).all { it.first === it.second }
        if (same) return
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    private fun setText(et: EditText, value: String, apply: (String) -> Unit) {
        // 先移除上一次绑定挂上的监听器，避免累积触发
        (et.getTag(R.string.tag_text_watcher) as? TextWatcher)?.let {
            et.removeTextChangedListener(it)
        }
        if (et.text?.toString() != value) {
            et.setText(value)
            et.setSelection(et.text?.length ?: 0)
        }
        val watcher = object : TextWatcher {
            var ignore = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (ignore) return
                ignore = true
                apply(s?.toString() ?: "")
                onChanged()
                ignore = false
            }
        }
        et.setTag(R.string.tag_text_watcher, watcher)
        et.addTextChangedListener(watcher)
    }
}
