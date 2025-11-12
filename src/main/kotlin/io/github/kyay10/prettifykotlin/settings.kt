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
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ListItemEditor
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.table.TableModelEditor
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import java.awt.Component
import java.io.Serializable
import com.intellij.openapi.options.ex.Settings.KEY as SettingsKey

data class RegexEntry(@Attribute var regex: String = "", @Attribute var replacement: String = "")
data class TokenEntry(val token: KtSingleValueToken, var replacement: String = "")

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

  abstract class Details(
    @NlsContexts.ConfigurableName val displayName: String,
    suffix: String
  ) {
    val id: String = "io.github.kyay10.prettifykotlin.settings$suffix"
  }
}

class SettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, Companion) {
  companion object : Details("Prettify Kotlin", "")

  override fun createPanel() = panel {
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

  // TODO validation
  private val identifierEditor =
    TableModelEditor(
      arrayOf(columnInfo("Regex", RegexEntry::regex), columnInfo("Replacement", RegexEntry::replacement)),
      object : ListItemEditor<RegexEntry> {
        override fun getItemClass() = RegexEntry::class.java
        override fun clone(item: RegexEntry, forInPlaceEditing: Boolean) = item.copy()
      }, "No replacements yet"
    )

  override fun createPanel() = panel {
    row {
      cell(identifierEditor.createComponent())
        .align(Align.FILL)
        .focused()
        .bind(
          { identifierEditor.model.items },
          { _, items -> identifierEditor.model.items = items.mapTo(ArrayList(items.size)) { it.copy() } },
          settings::identifierToReplacement.toMutableProperty(),
        )
    }.resizableRow()
  }
}

class TokenSettingsConfigurable(project: Project) : BaseSettingsConfigurable(project, Companion) {
  companion object : Details("Tokens", ".tokens")

  private val tokenModel = ListTableModel<TokenEntry>(
    columnInfo("Token", TokenEntry::token),
    columnInfo("Replacement", TokenEntry::replacement)
  )
  private val tokenTable = TableView(tokenModel)

  override fun createPanel() = panel {
    row {
      scrollCell(tokenTable)
        .align(Align.FILL)
        .focused()
        .bind(
          {
            tokenModel.items.filter { it.replacement != "" }
              .associate { (token, replacement) -> token.value to replacement }
          },
          { _, map ->
            tokenModel.items = allKeywords.values.map { TokenEntry(it, map[it.value] ?: "") }.toMutableList()
          },
          settings::tokenToReplacement.toMutableProperty()
        )
    }.resizableRow()
  }
}