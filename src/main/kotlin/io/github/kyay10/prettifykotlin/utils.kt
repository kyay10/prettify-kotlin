package io.github.kyay10.prettifykotlin

import arrow.core.getOrElse
import arrow.optics.Lens
import arrow.optics.Optional
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ColumnName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ImmutableColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import javax.swing.JCheckBox
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

fun Document?.prettyFoldingDescriptor(
  node: PsiElement,
  placeholder: String,
  range: TextRange = node.textRange,
  dependencies: Set<Any?> = setOf(),
) = FoldingDescriptor(node.node, range, null, dependencies, true, placeholder, true).apply {
  setCanBeRemovedWhenCollapsed(true)
}.takeIf { (range shl node.textRange.startOffset).substring(node.text) != placeholder }?.also {
  this ?: return@also
  for (highlighter in EditorFactory.getInstance().getEditors(this, node.project)
    .flatMap { it.markupModel.allHighlighters.toList() } +
    DocumentMarkupModel.forDocument(this, node.project, false)?.allHighlighters.orEmpty()) {
    if (highlighter.textRange.intersects(range) && highlighter is RangeHighlighterEx) {
      highlighter.isVisibleIfFolded = true
    }
  }
}

val KaCallableSymbol.fqName get() = callableId?.asSingleFqName()?.asString()

fun KaAnnotation.findConstantArgument(name: Name): Any? = findConstantArgument(name) { null }

inline fun <reified T> KaAnnotation.findConstantArgument(name: Name, default: () -> T): T {
  val constValue = (arguments.find { it.name == name }?.expression as? KaAnnotationValue.ConstantValue)?.value
  val value = constValue?.value
  if (constValue == null || value !is T) return default()
  return value
}

private val singleCharacterRange = TextRange(0, 1)

infix fun TextRange.shr(delta: Int) = shiftRight(delta)
infix fun TextRange.shl(delta: Int) = shiftLeft(delta)

val PsiElement.foldForSingleSpaceBefore: FoldingDescriptor?
  get() = prevSibling.takeIf { it.text.endsWith(" ") }?.let {
    null.prettyFoldingDescriptor(it, "", range = singleCharacterRange shr it.textRange.endOffset - 1)
  }

val PsiElement.foldForSingleSpaceAfter: FoldingDescriptor?
  get() = nextSibling.takeIf { it.text.startsWith(" ") }?.let {
    null.prettyFoldingDescriptor(it, "", range = singleCharacterRange shr it.textRange.startOffset)
  }

// Handle "as?" token properly. This is fixed in Kotlin 2.3.0-RC, so we make sure our representation matches that.
val KtSingleValueToken.code get() = if (this == KtTokens.AS_SAFE) "as?" else value

@Suppress("UNCHECKED_CAST")
val allKeywords = (IElementType.enumerate { it is KtSingleValueToken }.toList() as List<KtSingleValueToken>)
  .associateBy { it.code }.toSortedMap()

fun Project.updateFoldings() {
  EditorFactory.getInstance().allEditors.forEach {
    if (it.project == this) it.updateFoldings()
  }
}

fun Editor.updateFoldings() {
  val project = this.project ?: return
  if (this.virtualFile?.extension == "kt")
    CodeFoldingManager.getInstance(project).scheduleAsyncFoldingUpdate(this)
}

abstract class OpticsStateComponent<T : Any>(state: T) : SerializablePersistentStateComponent<T>(state) {
  fun <A> Lens<T, A>.update(value: A) {
    updateState { set(it, value) }
  }
}

operator fun <T : Any, A> Lens<T, A>.getValue(thisRef: OpticsStateComponent<T>, p: KProperty<*>): A =
  get(thisRef.state)

operator fun <T : Any, A> Lens<T, A>.setValue(thisRef: OpticsStateComponent<T>, p: KProperty<*>, value: A) =
  with(thisRef) { update(value) }

fun <Item> listTableModel(vararg columns: ColumnInfo<Item, *>) = ListTableModel<Item>(*columns)

fun <Item> tableView(model: ListTableModel<Item>, emptyText: String = "") = TableView(model).apply {
  this.emptyText.text = emptyText
  TableSpeedSearch.installOn(this)
}

fun <Item> Cell<*>.bindItems(model: ListTableModel<Item>, property: MutableProperty<List<Item>>) =
  bind({ model.items }, { _, items -> model.items = items.toMutableList() }, property)

fun <Item> Cell<*>.bindItems(model: ListTableModel<Item>, property: KMutableProperty0<List<Item>>) =
  bindItems(model, property.toMutableProperty())

inline infix fun <T, reified A> (T.() -> A).columnInfo(@ColumnName name: String) =
  object : ColumnInfo<T, A>(name) {
    override fun getColumnClass() = A::class.java
    override fun valueOf(item: T) = this@columnInfo(item)
  }

inline infix fun <T, reified A> Lens<T, A>.columnInfo(@ColumnName name: String) =
  object : ImmutableColumnInfo<T, A>(name) {
    override fun getColumnClass() = A::class.java
    override fun isCellEditable(item: T) = true
    override fun valueOf(item: T) = get(item)
    override fun withValue(item: T, value: A) = set(item, value)
    override fun setValue(item: T, value: A) {
      throw UnsupportedOperationException()
    }
  }

inline fun <T> MutableProperty<T>.modify(operation: (T) -> T) = set(operation(get()))

context(p: MutableProperty<T>)
val <T, A> Lens<T, A>.property
  get() = MutableProperty(
    { get(p.get()) },
    { value -> p.modify { set(it, value) } }
  )

context(p: MutableProperty<T?>)
fun <T, A> Lens<T, A>.property(default: A) = Optional.notNull<T>().compose(this).default(default).property

context(p: MutableProperty<T>)
fun <T, A, R> Lens<T, A>.focus(block: context(MutableProperty<A>) () -> R): R = block(property)

infix fun <T, A> MutableProperty<T>.compose(lens: Lens<T, A>) = with(this) { lens.property }

infix fun <T, A> KMutableProperty0<T>.compose(lens: Lens<T, A>) = toMutableProperty() compose lens

infix fun <T, A> Optional<T, A>.default(value: A): Lens<T, A> = Lens(
  get = { opt -> getOrModify(opt).getOrElse { value } },
  set = { opt, v -> set(opt, v) }
)

val <T> T.defaultLens: Lens<T?, Boolean>
  get() = Lens(
    get = { it != null },
    set = { a, value -> (a ?: this).takeIf { value } }
  )

fun <T> panel(property: MutableProperty<T>, init: context(MutableProperty<T>) Panel.() -> Unit) = panel {
  with(property) { init() }
}

fun <T> panel(property: KMutableProperty0<T>, init: context(MutableProperty<T>) Panel.() -> Unit) =
  panel(property.toMutableProperty(), init)


context(_: MutableProperty<T>)
fun <T, A> Panel.row(lens: Lens<T, A>, label: JLabel? = null, init: context(MutableProperty<A>) Row.() -> Unit) =
  row(label) {
    lens.focus { init() }
  }

context(_: MutableProperty<T>)
fun <T, A> Panel.row(lens: Lens<T, A>, @Nls label: String, init: context(MutableProperty<A>) Row.() -> Unit) =
  row(label) {
    lens.focus { init() }
  }

context(p: Panel, _: MutableProperty<T?>)
fun <T> optionsRows(@NlsContexts.Checkbox checkBoxText: String, default: T, init: Panel.() -> Unit) = p.rowsRange {
  lateinit var checkBox: Cell<JCheckBox>
  row { checkBox = checkBox(checkBoxText).bindSelected(default.defaultLens.property) }
  indent(init).enabledIf(checkBox.selected)
}

context(p: Panel, _: MutableProperty<T>)
fun <T, A> Lens<T, A?>.optionsRows(
  @NlsContexts.Checkbox checkBoxText: String,
  default: A,
  init: context(MutableProperty<A?>) Panel.() -> Unit
) = focus { optionsRows<A>(checkBoxText, default) { init(p) } }