@file:Suppress("CompanionObjectInExtension")

package io.github.kyay10.prettifykotlin

import arrow.optics.optics
import com.intellij.ide.DataManager.getInstance
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ElementProducer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import java.awt.Component
import java.io.Serializable
import com.intellij.openapi.options.ex.Settings.KEY as SettingsKey

@optics
data class RegexEntry(@Attribute val regex: String = "", @Attribute val replacement: String = "")

@optics
data class TokenEntry(val token: KtSingleValueToken, val replacement: String) {
  val code get() = token.code
}

@Service(Service.Level.PROJECT)
@State(name = "io.github.kyay10.prettifykotlin.Settings", storages = [Storage("prettify_kotlin.xml")])
class Settings : OpticsStateComponent<Settings.State>(State()) {
  @optics
  data class State(
    @Attribute("enabled") val isEnabled: Boolean = true,
    @XMap val tokenToReplacement: Map<String, String> = emptyMap(),
    @XCollection(style = XCollection.Style.v2) val identifierToReplacement: List<RegexEntry> = emptyList(),
  ) : Serializable

  var isEnabled by State.isEnabled
  var tokenToReplacement by State.tokenToReplacement
  var identifierToReplacement by State.identifierToReplacement
}

abstract class BaseSettingsConfigurable(val project: Project, details: Details) :
  BoundSearchableConfigurable(details.displayName, details.id) {
  val settings = project.service<Settings>()
  override fun apply() {
    super.apply()
    project.updateFoldings()
  }

  abstract fun Panel.fill(): Any?
  final override fun createPanel() = panel { fill() }

  abstract class Details(
    @NlsContexts.ConfigurableName val displayName: String, suffix: String
  ) {
    val id: String = "io.github.kyay10.prettifykotlin.settings$suffix"
  }
}

class SettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, Companion) {
  companion object : Details("Prettify Kotlin", "")

  override fun Panel.fill() {
    row { checkBox("Enable prettification").bindSelected(settings::isEnabled).focused() }.bottomGap(BottomGap.SMALL)
    panel {
      indent {
        for (configurable in listOf(IdentifierSettingsConfigurable, TokenSettingsConfigurable)) {
          row {
            @Suppress("DialogTitleCapitalization")
            link(configurable.displayName) { event ->
              val component = event.source as? Component
              val settings = SettingsKey.getData(getInstance().getDataContext(component)) ?: return@link
              settings.find(configurable.id)?.let(settings::select)
            }.customize(UnscaledGaps(bottom = 4))
          }
        }
      }
    }
  }
}

class IdentifierSettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, Companion) {
  companion object : Details("Identifiers", ".identifiers")

  override fun Panel.fill() = row {
    val model = listTableModel(RegexEntry.regex columnInfo "Regex", RegexEntry.replacement columnInfo "Replacement")
    cell(
      ToolbarDecorator.createDecorator(tableView(model, "No replacements"), object : ElementProducer<RegexEntry> {
        override fun canCreateElement() = true
        override fun createElement() = RegexEntry()
      }).createPanel()
    ).align(Align.FILL).focused().bindItems(model, settings::identifierToReplacement)
  }.resizableRow()
}

class TokenSettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, Companion) {
  companion object : Details("Tokens", ".tokens")

  override fun Panel.fill() = row {
    val model = listTableModel(TokenEntry::code columnInfo "Token", TokenEntry.replacement columnInfo "Replacement")
    scrollCell(TableView(model).also(TableSpeedSearch::installOn)).align(Align.FILL).focused().bindItems(model, {
      val map = settings.tokenToReplacement
      allKeywords.values.map { TokenEntry(it, map[it.code] ?: "") }
    }, {
      settings.tokenToReplacement = buildMap(it.size) {
        it.forEach { (token, replacement) -> if (replacement != "") put(token.code, replacement) }
      }
    })
  }.resizableRow()
}