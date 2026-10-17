package com.tuapp.eleuthera.desktop

import com.tuapp.eleuthera.platform.DesktopPaths
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/** Diálogo del sistema para elegir una carpeta. null si se cancela. */
object FolderPicker {
    fun choose(title: String = "Elige una carpeta con música"): File? {
        val start = File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory }
        return if (DesktopPaths.isMac) {
            // En macOS el diálogo nativo sabe elegir carpetas con esta propiedad.
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                start?.let { dialog.directory = it.path }
                dialog.isVisible = true
                dialog.file?.let { File(dialog.directory, it) }
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        } else {
            val chooser = JFileChooser(start).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
    }
}