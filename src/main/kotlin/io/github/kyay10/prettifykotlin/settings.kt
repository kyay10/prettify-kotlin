@file:Suppress("CompanionObjectInExtension")

package io.github.kyay10.prettifykotlin

import arrow.optics.Iso
import arrow.optics.optics
import com.intellij.ide.DataManager.getInstance
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.table.TableView
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.ElementProducer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
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

@optics
data class PrettyData(@Attribute val name: String = "", @Attribute val infixOnly: Boolean = false)

@optics
data class PrefixData(@Attribute val leftBracket: String = "", @Attribute val rightBracket: String = "")

@optics
data class PostfixData(@Attribute val dot: String = "")

@optics
data class AutoLambdaData(
  @Attribute val leftBrace: String = "",
  @Attribute val lambdaArrow: String = "->",
  @Attribute val rightBrace: String = ""
)

@optics
data class PrettyAttributes(
  @Property(surroundWithTag = false) val pretty: PrettyData? = null,
  @Property(surroundWithTag = false) val prefix: PrefixData? = null,
  @Property(surroundWithTag = false) val postfix: PostfixData? = null,
  @XMap val autoLambda: Map<String, AutoLambdaData> = emptyMap()
)

@Service(Service.Level.PROJECT)
@State(name = "io.github.kyay10.prettifykotlin.Settings", storages = [Storage("prettify_kotlin.xml")])
class Settings : OpticsStateComponent<Settings.State>(State()) {
  @optics
  data class State(
    @Attribute("enabled") val isEnabled: Boolean = true,
    @XMap val tokenToReplacement: Map<String, String> = emptyMap(),
    @XCollection(style = XCollection.Style.v2) val identifierToReplacement: List<RegexEntry> = emptyList(),
    @XMap val fqNameToPrettyData: Map<String, PrettyAttributes> = emptyMap(),
  ) : Serializable

  var isEnabled by State.isEnabled
  var tokenToReplacement by State.tokenToReplacement
  var identifierToReplacement by State.identifierToReplacement
  var fqNameToPrettyData by State.fqNameToPrettyData
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
        for (page in listOf(IdentifierSettingsConfigurable, TokenSettingsConfigurable, FqNameSettingsConfigurable)) {
          row {
            @Suppress("DialogTitleCapitalization")
            link(page.displayName) { event ->
              val component = event.source as? Component
              val settings = SettingsKey.getData(getInstance().getDataContext(component)) ?: return@link
              settings.find(page.id)?.let(settings::select)
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
    scrollCell(TableView(model).also(TableSpeedSearch::installOn)).align(Align.FILL).focused().bindItems(
      model,
      settings::tokenToReplacement compose Iso({ map ->
        allKeywords.values.map { TokenEntry(it, map[it.code] ?: "") }
      }, {
        buildMap(it.size) {
          it.forEach { (token, replacement) -> if (replacement != "") put(token.code, replacement) }
        }
      })
    )
  }.resizableRow()
}

internal class PrettyDetailsConfigurable(
  val originalName: String?,
  var prettyAttributes: PrettyAttributes,
  treeUpdate: Runnable
) :
  NamedConfigurable<PrettyAttributes>(true, treeUpdate) {
  var name: String = originalName ?: "path.to.some.function"
    private set
  val options: UnnamedConfigurable = object : DslConfigurableBase() {
    override fun createPanel(): DialogPanel = panel(::prettyAttributes) {
      indent {
        PrettyAttributes.pretty.optionsRows("Pretty", PrettyData()) {
          row { textField().bindText(PrettyData.name.property("")).label("Name:") }.layout(RowLayout.PARENT_GRID)
          row { checkBox("Infix only").bindSelected(PrettyData.infixOnly.property(false)) }.layout(RowLayout.PARENT_GRID)
        }
        separator()
        PrettyAttributes.prefix.optionsRows("Prefix", PrefixData()) {
          row {
            textField().bindText(PrefixData.leftBracket.property("")).label("Left bracket:")
          }.layout(RowLayout.PARENT_GRID)
          row {
            textField().bindText(PrefixData.rightBracket.property("")).label("Right bracket:")
          }.layout(RowLayout.PARENT_GRID)
        }
        separator()
        PrettyAttributes.postfix.optionsRows("Postfix", PostfixData()) {
          row { textField().bindText(PostfixData.dot.property("")).label("Dot:") }.layout(RowLayout.PARENT_GRID)
        }
        // TODO AutoLambda table
      }
    }
  }

  override fun getDisplayName() = name
  override fun setDisplayName(name: String) {
    this.name = name
  }

  override fun getEditableObject() = prettyAttributes
  override fun getBannerSlogan() = name
  override fun createOptionsPanel() = options.createComponent()
  override fun isModified(): Boolean = options.isModified || name != originalName
  override fun apply() = options.apply()
  override fun reset() = options.reset()
  override fun disposeUIResources() {
    super.disposeUIResources()
    options.disposeUIResources()
  }
}

class FqNameSettingsConfigurable(private val project: Project) : MasterDetailsComponent(), SearchableConfigurable {
  companion object : BaseSettingsConfigurable.Details("Fully Qualified Names", ".fqn")

  val settings = project.service<Settings>()

  init {
    initTree()
    myTree.emptyText.text = "No FQN replacements"
  }

  override fun getDisplayName() = Companion.displayName
  override fun getId() = Companion.id
  override fun getHelpTopic() = Companion.id

  override fun getEmptySelectionString() = "Select an FQN replacement to view or edit its details"

  override fun isModified() =
    settings.fqNameToPrettyData.size != myRoot.childCount || super<MasterDetailsComponent>.isModified

  override fun apply() {
    super.apply()
    settings.fqNameToPrettyData = buildMap(myRoot.childCount) {
      for (child in myRoot.children()) {
        val node = child as MyNode
        val configurable = node.configurable as PrettyDetailsConfigurable
        put(configurable.name, configurable.prettyAttributes)
      }
    }
    project.updateFoldings()
  }

  override fun reset() {
    myRoot.removeAllChildren()
    settings.fqNameToPrettyData.forEach { (name, data) -> addChild(name, data) }
    super<MasterDetailsComponent>.reset()
  }

  private fun addChild(name: String?, prettyAttributes: PrettyAttributes) =
    addChild(PrettyDetailsConfigurable(name, prettyAttributes, TREE_UPDATER))

  private fun addChild(details: PrettyDetailsConfigurable) =
    MyNode(details).also { addNode(it, myRoot) }

  override fun createActions(fromPopup: Boolean): List<AnAction> = mutableListOf(
    AddAction(),
    MyDeleteAction(),
    DuplicateAction()
  )

  private inner class DuplicateAction : DumbAwareAction(
    "Duplicate",
    "Duplicate",
    PlatformIcons.COPY_ICON
  ) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDuplicate(), myTree)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectedTarget != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      selectedTarget?.let { target ->
        selectNodeInTree(addChild(target.name, target.prettyAttributes), true, true)
      }
    }

    private val selectedTarget get() = selectedNode?.configurable as? PrettyDetailsConfigurable
  }

  private inner class AddAction : DumbAwareAction(
    "Add",
    "Add",
    PlatformIcons.ADD_ICON
  ) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getNew(), myTree)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      selectNodeInTree(addChild(null, PrettyAttributes()), true, true)
    }
  }
}