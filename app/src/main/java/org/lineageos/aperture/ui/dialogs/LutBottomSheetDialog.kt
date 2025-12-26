/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.lineageos.aperture.R
import org.lineageos.aperture.models.Lut

class LutBottomSheetDialog(
    context: Context,
    private val luts: List<Lut>,
    private val selectedLutId: String?,
    private val onImport: () -> Unit,
    private val onSelect: (Lut?) -> Unit,
    private val onDelete: (Lut) -> Unit
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lut_bottom_sheet_dialog)

        findViewById<Button>(R.id.importButton)?.setOnClickListener {
            onImport()
            dismiss()
        }

        findViewById<Button>(R.id.noneButton)?.setOnClickListener {
            onSelect(null)
            dismiss()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = LutAdapter()
    }

    private inner class LutAdapter : RecyclerView.Adapter<LutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.lutName)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lut, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lut = luts[position]
            holder.nameText.text = lut.name
            
            if (lut.id == selectedLutId) {
                holder.nameText.setTextColor(Color.GREEN) // Highlight selected
            } else {
                holder.nameText.setTextColor(Color.WHITE)
            }

            holder.itemView.setOnClickListener {
                onSelect(lut)
                dismiss()
            }

            holder.deleteButton.setOnClickListener {
                onDelete(lut)
                // We'd need to refresh list, but since dialog dismisses/needs reload, simpler to just close or rely on caller to show updated dialog
                dismiss() 
            }
        }

        override fun getItemCount() = luts.size
    }
}
