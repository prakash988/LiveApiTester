package com.liveapitester.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.liveapitester.collections.ApiCollection
import com.liveapitester.collections.CollectionManager
import com.liveapitester.collections.SavedRequest
import com.liveapitester.http.ApiRequest
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.*

class CollectionsPanel(
    private val project: Project,
    private val onSelect: (SavedRequest) -> Unit
) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Collections")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = JTree(treeModel)

    init {
        setupUI()
        refresh()
    }

    private fun setupUI() {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = CollectionTreeCellRenderer()

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    when (val userObj = node.userObject) {
                        is SavedRequest -> onSelect(userObj)
                    }
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    tree.selectionPath = path
                    showContextMenu(e, path)
                }
            }
        })

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        val addCollectionBtn = JButton(AllIcons.General.Add)
        addCollectionBtn.toolTipText = "New Collection"
        addCollectionBtn.addActionListener { addCollection() }
        toolbar.add(addCollectionBtn)

        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
    }

    private fun showContextMenu(e: MouseEvent, path: TreePath) {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val menu = JPopupMenu()

        when (val userObj = node.userObject) {
            is ApiCollection -> {
                menu.add(JMenuItem("Add Request").also { item ->
                    item.addActionListener { addRequestToCollection(userObj) }
                })
                menu.add(JMenuItem("Delete Collection").also { item ->
                    item.addActionListener { deleteCollection(userObj) }
                })
            }
            is SavedRequest -> {
                val collectionNode = node.parent as? DefaultMutableTreeNode ?: return
                val collection = collectionNode.userObject as? ApiCollection ?: return
                menu.add(JMenuItem("Delete Request").also { item ->
                    item.addActionListener {
                        CollectionManager.getInstance(project).removeRequestFromCollection(collection.id, userObj.id)
                        refresh()
                    }
                })
            }
        }

        menu.show(tree, e.x, e.y)
    }

    private fun addCollection() {
        val name = Messages.showInputDialog(
            project,
            "Collection name:",
            "New Collection",
            null
        )
        if (!name.isNullOrBlank()) {
            CollectionManager.getInstance(project).addCollection(ApiCollection(name = name))
            refresh()
        }
    }

    private fun deleteCollection(collection: ApiCollection) {
        val confirm = Messages.showYesNoDialog(
            project,
            "Delete collection '${collection.name}'?",
            "Delete Collection",
            Messages.getQuestionIcon()
        )
        if (confirm == Messages.YES) {
            CollectionManager.getInstance(project).removeCollection(collection.id)
            refresh()
        }
    }

    private fun addRequestToCollection(collection: ApiCollection) {
        val name = Messages.showInputDialog(
            project,
            "Request name:",
            "Save Request",
            null
        )
        if (!name.isNullOrBlank()) {
            val savedRequest = SavedRequest(name = name)
            CollectionManager.getInstance(project).addRequestToCollection(collection.id, savedRequest)
            refresh()
        }
    }

    fun saveCurrentRequest(request: ApiRequest) {
        val collections = CollectionManager.getInstance(project).getCollections()
        if (collections.isEmpty()) {
            Messages.showInfoMessage(project, "No collections found. Create a collection first.", "No Collections")
            return
        }
        val collectionNames = collections.map { it.name }.toTypedArray()
        val selected = Messages.showChooseDialog(
            project,
            "Select collection to save to:",
            "Save Request",
            null,
            collectionNames,
            collectionNames[0]
        )
        if (selected != null) {
            val collection = collections.find { it.name == selected } ?: return
            val name = Messages.showInputDialog(project, "Request name:", "Save Request", null)
            if (!name.isNullOrBlank()) {
                CollectionManager.getInstance(project).addRequestToCollection(
                    collection.id,
                    SavedRequest(name = name, request = request)
                )
                refresh()
            }
        }
    }

    fun refresh() {
        rootNode.removeAllChildren()
        val manager = CollectionManager.getInstance(project)
        for (collection in manager.getCollections()) {
            val collectionNode = DefaultMutableTreeNode(collection)
            for (savedRequest in collection.requests) {
                collectionNode.add(DefaultMutableTreeNode(savedRequest))
            }
            rootNode.add(collectionNode)
        }
        treeModel.reload()
        for (i in 0 until tree.rowCount) tree.expandRow(i)
    }

    private class CollectionTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any, selected: Boolean,
            expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode ?: return this
            when (val userObj = node.userObject) {
                is ApiCollection -> {
                    text = userObj.name
                    icon = AllIcons.Nodes.Folder
                }
                is SavedRequest -> {
                    text = "[${userObj.request.method}] ${userObj.name}"
                    icon = AllIcons.Nodes.Method
                }
                else -> text = value.toString()
            }
            return this
        }
    }
}
