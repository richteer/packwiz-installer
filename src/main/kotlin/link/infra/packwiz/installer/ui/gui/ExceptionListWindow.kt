package link.infra.packwiz.installer.ui.gui

import link.infra.packwiz.installer.ui.IUserInterface
import link.infra.packwiz.installer.ui.data.ExceptionDetails
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.border.EmptyBorder

class ExceptionListWindow(eList: List<ExceptionDetails>, future: CompletableFuture<IUserInterface.ExceptionListResult>, numTotal: Int, allowsIgnore: Boolean, parentWindow: JFrame?) : JDialog(parentWindow, "Failed file downloads", true) {
	private val lblExceptionStacktrace: JTextArea

	private class ExceptionListModel(private val details: List<ExceptionDetails>) : AbstractListModel<String>() {
		override fun getSize() = details.size
		override fun getElementAt(index: Int) = details[index].name
		fun getExceptionAt(index: Int) = details[index].exception
	}

	/**
	 * Create the dialog.
	 */
	init {
		setBounds(100, 100, 540, 340)
		setLocationRelativeTo(parentWindow)

		contentPane.apply {
			layout = BorderLayout()

			// Error panel
			add(JPanel().apply {
				add(JLabel("One or more errors were encountered while installing the modpack!").apply {
					icon = UIManager.getIcon("OptionPane.warningIcon")
				})
			}, BorderLayout.NORTH)

			// Content panel
			add(JPanel().apply {
				border = EmptyBorder(5, 5, 5, 5)
				layout = BorderLayout(0, 0)

				add(JSplitPane().apply {
					resizeWeight = 0.3

					lblExceptionStacktrace = JTextArea("Select a file")
					lblExceptionStacktrace.background = UIManager.getColor("List.background")
					lblExceptionStacktrace.isOpaque = true
					lblExceptionStacktrace.wrapStyleWord = true
					lblExceptionStacktrace.lineWrap = true
					lblExceptionStacktrace.isEditable = false
					lblExceptionStacktrace.isFocusable = true
					lblExceptionStacktrace.font = UIManager.getFont("Label.font")
					lblExceptionStacktrace.border = EmptyBorder(5, 5, 5, 5)

					rightComponent = JScrollPane(lblExceptionStacktrace)

					leftComponent = JScrollPane(JList<String>().apply {
						selectionMode = ListSelectionModel.SINGLE_SELECTION
						border = EmptyBorder(5, 5, 5, 5)
						val listModel = ExceptionListModel(eList)
						model = listModel
						addListSelectionListener {
							val i = selectedIndex
							if (i > -1) {
								val sw = StringWriter()
								listModel.getExceptionAt(i).printStackTrace(PrintWriter(sw))
								lblExceptionStacktrace.text = sw.toString()
								// Scroll to the top
								lblExceptionStacktrace.caretPosition = 0
							} else {
								lblExceptionStacktrace.text = "Select a file"
							}
						}
					})
				})
			}, BorderLayout.CENTER)

			// Button pane
			add(JPanel().apply {
				layout = BorderLayout(0, 0)

				// Right buttons
				add(JPanel().apply {
					add(JButton("Continue").apply {
						toolTipText = "Attempt to continue installing, excluding the failed downloads"
						addActionListener {
							future.complete(IUserInterface.ExceptionListResult.CONTINUE)
							this@ExceptionListWindow.dispose()
						}
					})

					add(JButton("Cancel launch").apply {
						toolTipText = "Stop launching the game"
						addActionListener {
							future.complete(IUserInterface.ExceptionListResult.CANCEL)
							this@ExceptionListWindow.dispose()
						}
					})

					add(JButton("Ignore update").apply {
						toolTipText = "Start the game without attempting to update"
						isEnabled = allowsIgnore
						addActionListener {
							future.complete(IUserInterface.ExceptionListResult.IGNORE)
							this@ExceptionListWindow.dispose()
						}
					})
				}, BorderLayout.EAST)

				// Errored label
				add(JLabel(eList.size.toString() + "/" + numTotal + " errored").apply {
					horizontalAlignment = SwingConstants.CENTER
				}, BorderLayout.CENTER)

				// Left buttons
				add(JPanel().apply {
					add(JButton("Report issue").apply {
						if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
							addActionListener {
								try {
									Desktop.getDesktop().browse(URI("https://github.com/packwiz/packwiz-installer/issues/new"))
								} catch (e: IOException) {
									// lol the button just won't work i guess
								} catch (e: URISyntaxException) {}
							}
						} else {
							isEnabled = false
						}
					})
				}, BorderLayout.WEST)
			}, BorderLayout.SOUTH)
		}

		addWindowListener(object : WindowAdapter() {
			override fun windowClosing(e: WindowEvent) {
				future.complete(IUserInterface.ExceptionListResult.CANCEL)
			}

			override fun windowClosed(e: WindowEvent) {
				// Just in case closing didn't get triggered - if something else called dispose() the
				// future will have already completed
				future.complete(IUserInterface.ExceptionListResult.CANCEL)
			}
		})
	}
}