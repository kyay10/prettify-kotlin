package io.github.kyay10.prettifykotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import javax.swing.JComponent
import javax.swing.JPanel

data class RegexToReplacement(
  @Attribute(converter = RegexConverter::class)
  val regex: Regex,
  val replacement: String,
)

private val defaultTokenReplacements = mapOf(
  KtTokens.IN_KEYWORD to "∈",
  KtTokens.NOT_IN to "∉",
  KtTokens.FOR_KEYWORD to "∀",
)

private val defaultIdentifierReplacements = mapOf(
  "^shl$".toRegex() to "<<",
  "^shr$".toRegex() to ">>",
  "^ushr$".toRegex() to ">>>",
  "^and$".toRegex() to "&",
  "^or$".toRegex() to "|",
  "^xor$".toRegex() to "^",
  "^`([^`]+)`$".toRegex() to "$1",
)

@State(name = "io.github.kyay10.prettifykotlin.AppSettings", storages = [Storage("PrettifyKotlinPlugin.xml")])
internal class AppSettings : SimplePersistentStateComponent<AppSettings.State>(State()) {

  internal data class State(
    var isEnabled: Boolean = true,
    @Attribute(converter = KtSingleValueTokenConverter::class)
    val tokenToReplacement: MutableMap<KtSingleValueToken, String> = mutableMapOf(),

    @XCollection(style = XCollection.Style.v2)
    val identifierToReplacement: MutableList<Pair<Regex, String>> = mutableListOf()
  ) : BaseState()

  companion object {
    val instance: AppSettings?
      get() = ApplicationManager.getApplication()
        .getService(AppSettings::class.java)

  }
}

object RegexConverter : Converter<Regex>() {
  override fun fromString(value: String): Regex = value.toRegex()

  override fun toString(value: Regex): String = value.pattern
}

object KtSingleValueTokenConverter : Converter<KtSingleValueToken>() {
  override fun fromString(value: String): KtSingleValueToken? = allKeywords[value]

  override fun toString(value: KtSingleValueToken): String = value.value
}

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
  private val enabled = JBCheckBox("Enable prettification", true)
  val panel: JPanel = FormBuilder.createFormBuilder().apply {
    addComponent(enabled, 1)
    addComponentFillVertically(JPanel(), 0)
  }.panel


  var isEnabled: Boolean
    get() = enabled.isSelected
    set(value) {
      enabled.isSelected = value
    }
}

/**
 * Provides controller functionality for application settings.
 */
internal class AppSettingsConfigurable : Configurable {
  private var _component: AppSettingsComponent? = null
  private val component: AppSettingsComponent
    get() = _component!!

  @Nls(capitalization = Nls.Capitalization.Title)
  override fun getDisplayName(): @NlsContexts.ConfigurableName String = "SDK: Application Settings Example"

  override fun getPreferredFocusedComponent() = null

  override fun createComponent(): JComponent {
    _component = AppSettingsComponent()
    return component.panel
  }

  override fun isModified(): Boolean {
    val state: AppSettings.State = AppSettings.instance?.state!!
    return state.isEnabled != component.isEnabled
  }


  override fun apply() {
    AppSettings.instance?.state!!.apply {
      isEnabled = component.isEnabled
    }
  }

  override fun reset() = with(AppSettings.instance?.state!!) {
    component.isEnabled = isEnabled
  }

  override fun disposeUIResources() {
    _component = null
  }
}