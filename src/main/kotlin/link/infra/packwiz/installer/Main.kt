@file:JvmName("Main")

package link.infra.packwiz.installer

import link.infra.packwiz.installer.target.Side
import link.infra.packwiz.installer.target.path.HttpUrlPath
import link.infra.packwiz.installer.target.path.PackwizFilePath
import link.infra.packwiz.installer.ui.cli.CLIHandler
import link.infra.packwiz.installer.ui.gui.GUIHandler
import link.infra.packwiz.installer.ui.wrap
import link.infra.packwiz.installer.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.net.URI
import java.nio.file.Paths
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess

@Suppress("unused")
class Main(args: Array<String>) {
	// Don't attempt to start a GUI if we are headless
	private var guiEnabled = !GraphicsEnvironment.isHeadless()

	private fun startup(args: Array<String>) {
		val options = Options()
		addNonBootstrapOptions(options)
		addBootstrapOptions(options)

		val parser = DefaultParser()
		val cmd = try {
			parser.parse(options, args)
		} catch (e: ParseException) {
			Log.fatal("Failed to parse command line arguments", e)
			if (guiEnabled) {
				EventQueue.invokeAndWait {
					try {
						UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
					} catch (ignored: Exception) {
						// Ignore the exceptions, just continue using the ugly L&F
					}
					JOptionPane.showMessageDialog(null, "Failed to parse command line arguments: $e",
						"packwiz-installer", JOptionPane.ERROR_MESSAGE)
				}
			}
			exitProcess(1)
		}

		if (guiEnabled && cmd.hasOption("no-gui")) {
			guiEnabled = false
		}

		val ui = if (guiEnabled) GUIHandler() else CLIHandler()

		val unparsedArgs = cmd.args
		if (unparsedArgs.size > 1) {
			ui.showErrorAndExit("Too many arguments specified!")
		} else if (unparsedArgs.isEmpty()) {
			ui.showErrorAndExit("pack.toml URI to install from must be specified!")
		}

		val title = cmd.getOptionValue("title")
		if (title != null) {
			ui.title = title
		}

		ui.show()

		val packFileRaw = unparsedArgs[0]

		val packFile = when {
			// HTTP(s) URLs
			Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(packFileRaw) -> ui.wrap("Invalid HTTP/HTTPS URL for pack file: $packFileRaw") {
				HttpUrlPath(packFileRaw.toHttpUrl().resolve(".")!!, packFileRaw.toHttpUrl().pathSegments.last())
			}
			// File URIs (uses same logic as old packwiz-installer, for backwards compat)
			Regex("^file:", RegexOption.IGNORE_CASE).containsMatchIn(packFileRaw) -> {
				ui.wrap("Failed to parse file path for pack file: $packFileRaw") {
					val path = Paths.get(URI(packFileRaw)).toOkioPath()
					PackwizFilePath(path.parent ?: ui.showErrorAndExit("Invalid pack file path: $packFileRaw"), path.name)
				}
			}
			// Other URIs (unsupported)
			Regex("^[a-z][a-z\\d+\\-.]*://", RegexOption.IGNORE_CASE).containsMatchIn(packFileRaw) -> ui.showErrorAndExit("Unsupported scheme for pack file: $packFileRaw")
			// None of the above matches -> interpret as file path
			else -> PackwizFilePath(packFileRaw.toPath().parent ?: ui.showErrorAndExit("Invalid pack file path: $packFileRaw"), packFileRaw.toPath().name)
		}
		val side = cmd.getOptionValue("side")?.let {
			Side.from(it) ?: ui.showErrorAndExit("Unknown side name: $it")
		} ?: Side.CLIENT
		val packFolder = ui.wrap("Invalid pack folder path") {
			cmd.getOptionValue("pack-folder")?.let{ PackwizFilePath(it.toPath()) } ?: PackwizFilePath(".".toPath())
		}
		val multimcFolder = ui.wrap("Invalid MultiMC folder path") {
			cmd.getOptionValue("multimc-folder")?.let{ PackwizFilePath(it.toPath()) } ?: PackwizFilePath("..".toPath())
		}
		val manifestFile = ui.wrap("Invalid manifest file path") {
			packFolder / (cmd.getOptionValue("meta-file") ?: "packwiz.json")
		}

		// Start update process!
		try {
			UpdateManager(UpdateManager.Options(packFile, manifestFile, packFolder, multimcFolder, side), ui)
		} catch (e: Exception) {
			ui.showErrorAndExit("Update process failed", e)
		}
		println("Finished successfully!")
		ui.dispose()
	}

	companion object {
		// Called by packwiz-installer-bootstrap to set up the help command
		@JvmStatic
		fun addNonBootstrapOptions(options: Options) {
			options.addOption("s", "side", true, "Side to install mods from (client/server, defaults to client)")
			options.addOption(null, "title", true, "Title of the installer window")
			options.addOption(null, "pack-folder", true, "Folder to install the pack to (defaults to the JAR directory)")
			options.addOption(null, "multimc-folder", true, "The MultiMC pack folder (defaults to the parent of the pack directory)")
			options.addOption(null, "meta-file", true, "JSON file to store pack metadata, relative to the pack folder (defaults to packwiz.json)")
		}

		// TODO: link these somehow so they're only defined once?
		@JvmStatic
		private fun addBootstrapOptions(options: Options) {
			options.addOption(null, "bootstrap-update-url", true, "Github API URL for checking for updates")
			options.addOption(null, "bootstrap-update-token", true, "Github API Access Token, for private repositories")
			options.addOption(null, "bootstrap-no-update", false, "Don't update packwiz-installer")
			options.addOption(null, "bootstrap-main-jar", true, "Location of the packwiz-installer JAR file")
			options.addOption("g", "no-gui", false, "Don't display a GUI to show update progress")
			options.addOption("h", "help", false, "Display this message") // Implemented in packwiz-installer-bootstrap!
		}
	}

	// Actual main() is in RequiresBootstrap!
	init {
		// Big overarching try/catch just in case everything breaks
		try {
			startup(args)
		} catch (e: Exception) {
			Log.fatal("Error from main", e)
			if (guiEnabled) {
				EventQueue.invokeLater {
					JOptionPane.showMessageDialog(null,
						"A fatal error occurred: \n$e",
						"packwiz-installer", JOptionPane.ERROR_MESSAGE)
					exitProcess(1)
				}
				// In case the EventQueue is broken, exit after 1 minute
				Thread.sleep(60 * 1000.toLong())
			}
			exitProcess(1)
		}
	}
}